// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.spanner.pgadapter.utils;

import com.google.api.core.InternalApi;
import com.google.cloud.spanner.pgadapter.ConnectionHandler;
import com.google.cloud.spanner.pgadapter.statements.local.DjangoGetTableNamesStatement;
import com.google.cloud.spanner.pgadapter.statements.local.ListDatabasesStatement;
import com.google.cloud.spanner.pgadapter.statements.local.LocalStatement;
import com.google.cloud.spanner.pgadapter.statements.local.SelectCurrentCatalogStatement;
import com.google.cloud.spanner.pgadapter.statements.local.SelectCurrentDatabaseStatement;
import com.google.cloud.spanner.pgadapter.statements.local.SelectCurrentSchemaStatement;
import com.google.cloud.spanner.pgadapter.statements.local.SelectVersionStatement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.postgresql.core.Oid;

/**
 * Utility class that tries to automatically detect well-known clients and drivers that are
 * connecting to PGAdapter.
 */
@InternalApi
public class ClientAutoDetector {
  public static final ImmutableList<LocalStatement> EMPTY_LOCAL_STATEMENTS = ImmutableList.of();
  public static final ImmutableList<LocalStatement> DEFAULT_LOCAL_STATEMENTS =
      ImmutableList.of(
          SelectCurrentSchemaStatement.INSTANCE,
          SelectCurrentDatabaseStatement.INSTANCE,
          SelectCurrentCatalogStatement.INSTANCE,
          SelectVersionStatement.INSTANCE,
          DjangoGetTableNamesStatement.INSTANCE);

  public enum WellKnownClient {
    PSQL {

      @Override
      boolean isClient(List<String> orderedParameterKeys, Map<String, String> parameters) {
        // PSQL makes it easy for us, as it sends its own name in the application_name parameter.
        return parameters.containsKey("application_name")
            && parameters.get("application_name").equals("psql");
      }

      @Override
      public ImmutableList<LocalStatement> getLocalStatements(ConnectionHandler connectionHandler) {
        if (connectionHandler.getServer().getOptions().useDefaultLocalStatements()) {
          return ImmutableList.<LocalStatement>builder()
              .addAll(DEFAULT_LOCAL_STATEMENTS)
              .add(new ListDatabasesStatement(connectionHandler))
              .build();
        }
        return ImmutableList.of(new ListDatabasesStatement(connectionHandler));
      }
    },
    JDBC {
      @Override
      boolean isClient(List<String> orderedParameterKeys, Map<String, String> parameters) {
        // JDBC always sends the following startup parameters as the first five parameters (and has
        // done so for more than 5 years):
        // paramList.add(new String[]{"user", user});
        // paramList.add(new String[]{"database", database});
        // paramList.add(new String[]{"client_encoding", "UTF8"});
        // paramList.add(new String[]{"DateStyle", "ISO"});
        // paramList.add(new String[]{"TimeZone", createPostgresTimeZone()});
        if (orderedParameterKeys.size() < 5) {
          return false;
        }
        if (!orderedParameterKeys.get(0).equals("user")) {
          return false;
        }
        if (!orderedParameterKeys.get(1).equals("database")) {
          return false;
        }
        if (!orderedParameterKeys.get(2).equals("client_encoding")) {
          return false;
        }
        if (!orderedParameterKeys.get(3).equals("DateStyle")) {
          return false;
        }
        if (!orderedParameterKeys.get(4).equals("TimeZone")) {
          return false;
        }
        if (!parameters.get("client_encoding").equals("UTF8")) {
          return false;
        }
        return parameters.get("DateStyle").equals("ISO");
      }

      @Override
      public ImmutableMap<String, String> getDefaultParameters() {
        return ImmutableMap.of(
            "spanner.guess_types", String.format("%d,%d", Oid.TIMESTAMPTZ, Oid.DATE));
      }
    },
    PGX {
      @Override
      boolean isClient(List<String> orderedParameterKeys, Map<String, String> parameters) {
        // pgx does not send enough unique parameters for it to be auto-detected.
        return false;
      }
    },
    UNSPECIFIED {
      @Override
      boolean isClient(List<String> orderedParameterKeys, Map<String, String> parameters) {
        // Use UNSPECIFIED as default to prevent null checks everywhere and to ease the use of any
        // defaults defined in this enum.
        return true;
      }
    };

    abstract boolean isClient(List<String> orderedParameterKeys, Map<String, String> parameters);

    public ImmutableList<LocalStatement> getLocalStatements(ConnectionHandler connectionHandler) {
      if (connectionHandler.getServer().getOptions().useDefaultLocalStatements()) {
        return DEFAULT_LOCAL_STATEMENTS;
      }
      return EMPTY_LOCAL_STATEMENTS;
    }

    public ImmutableMap<String, String> getDefaultParameters() {
      return ImmutableMap.of();
    }
  }

  /**
   * Returns the {@link WellKnownClient} that the detector thinks is connecting to PGAdapter based
   * purely on the list of parameters. It will return UNSPECIFIED if no specific client could be
   * determined.
   */
  public static @Nonnull WellKnownClient detectClient(
      List<String> orderParameterKeys, Map<String, String> parameters) {
    for (WellKnownClient client : WellKnownClient.values()) {
      if (client.isClient(orderParameterKeys, parameters)) {
        return client;
      }
    }
    // The following line should never be reached.
    throw new IllegalStateException("UNSPECIFIED.isClient() should have returned true");
  }
}
