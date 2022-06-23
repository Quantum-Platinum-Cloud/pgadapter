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

import com.google.cloud.spanner.connection.Connection;
import com.google.cloud.spanner.connection.StatementResult;

/**
 * Interface for statements that are handled locally in PGAdapter instead of being sent to
 * Connection API.
 */
public interface LocalStatement {
  /** Returns the static SQL string associated with this local statement. */
  String getSql();

  /** Executes the local statement and returns the result. */
  StatementResult execute(Connection connection);
}
