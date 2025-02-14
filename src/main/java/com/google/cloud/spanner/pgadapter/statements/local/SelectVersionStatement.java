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

package com.google.cloud.spanner.pgadapter.statements.local;

import com.google.api.core.InternalApi;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.ResultSets;
import com.google.cloud.spanner.Struct;
import com.google.cloud.spanner.Type;
import com.google.cloud.spanner.Type.StructField;
import com.google.cloud.spanner.connection.StatementResult;
import com.google.cloud.spanner.pgadapter.statements.BackendConnection;
import com.google.cloud.spanner.pgadapter.statements.BackendConnection.QueryResult;
import com.google.common.collect.ImmutableList;

@InternalApi
public class SelectVersionStatement implements LocalStatement {
  public static final SelectVersionStatement INSTANCE = new SelectVersionStatement();

  private SelectVersionStatement() {}

  @Override
  public String[] getSql() {
    return new String[] {
      "select version()",
      "SELECT version()",
      "Select version()",
      "select VERSION()",
      "SELECT VERSION()",
      "Select VERSION()",
      "select * from version()",
      "SELECT * FROM version()",
      "Select * from version()",
      "select * from VERSION()",
      "SELECT * FROM VERSION()",
      "Select * from VERSION()",
      "select pg_catalog.version()",
      "SELECT pg_catalog.version()",
      "Select pg_catalog.version()",
      "select PG_CATALOG.VERSION()",
      "SELECT PG_CATALOG.VERSION()",
      "Select PG_CATALOG.VERSION()",
      "select * from pg_catalog.version()",
      "SELECT * FROM pg_catalog.version()",
      "Select * from pg_catalog.version()",
      "select * from PG_CATALOG.VERSION()",
      "SELECT * FROM PG_CATALOG.VERSION()",
      "Select * from PG_CATALOG.VERSION()"
    };
  }

  @Override
  public StatementResult execute(BackendConnection backendConnection) {
    ResultSet resultSet =
        ResultSets.forRows(
            Type.struct(StructField.of("version", Type.string())),
            ImmutableList.of(
                Struct.newBuilder()
                    .set("version")
                    .to(
                        "PostgreSQL "
                            + backendConnection
                                .getSessionState()
                                .get(null, "server_version")
                                .getSetting())
                    .build()));
    return new QueryResult(resultSet);
  }
}
