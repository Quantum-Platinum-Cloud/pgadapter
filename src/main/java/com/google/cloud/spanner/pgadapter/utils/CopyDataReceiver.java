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
import com.google.cloud.spanner.ErrorCode;
import com.google.cloud.spanner.SpannerExceptionFactory;
import com.google.cloud.spanner.pgadapter.ConnectionHandler;
import com.google.cloud.spanner.pgadapter.ConnectionHandler.ConnectionStatus;
import com.google.cloud.spanner.pgadapter.error.PGExceptionFactory;
import com.google.cloud.spanner.pgadapter.statements.CopyStatement;
import com.google.cloud.spanner.pgadapter.statements.IntermediateStatement.ResultNotReadyBehavior;
import com.google.cloud.spanner.pgadapter.wireoutput.CommandCompleteResponse;
import com.google.cloud.spanner.pgadapter.wireoutput.CopyInResponse;
import com.google.common.annotations.VisibleForTesting;
import java.util.concurrent.Callable;

/**
 * This message receiver is activated when the COPY sub-protocol is activated by a COPY statement.
 * This handler will run and receive all incoming messages until the state of the connection changes
 * from COPY_IN to either COPY_DONE or COPY_FAILED.
 */
@InternalApi
public class CopyDataReceiver implements Callable<Void> {
  private final CopyStatement copyStatement;
  private final ConnectionHandler connectionHandler;

  public CopyDataReceiver(CopyStatement copyStatement, ConnectionHandler connectionHandler) {
    this.copyStatement = copyStatement;
    this.connectionHandler = connectionHandler;
  }

  @Override
  public Void call() throws Exception {
    handleCopy();
    return null;
  }

  /**
   * Sends a {@link CopyInResponse} to the client and then waits for copy data, done and fail
   * messages. The incoming data messages are fed into the {@link MutationWriter} that is associated
   * with this {@link CopyStatement}. The method blocks until it sees a {@link
   * com.google.cloud.spanner.pgadapter.wireprotocol.CopyDoneMessage} or {@link
   * com.google.cloud.spanner.pgadapter.wireprotocol.CopyFailMessage}, or until the {@link
   * MutationWriter} changes the status of the connection to a non-COPY_IN status, for example as a
   * result of an error while copying the data to Cloud Spanner.
   */
  @VisibleForTesting
  void handleCopy() throws Exception {
    if (copyStatement.hasException()) {
      throw copyStatement.getException();
    } else {
      this.connectionHandler.setActiveCopyStatement(copyStatement);
      new CopyInResponse(
              this.connectionHandler.getConnectionMetadata().getOutputStream(),
              copyStatement.getTableColumns().size(),
              copyStatement.getFormatCode())
          .send();
      ConnectionStatus initialConnectionStatus = this.connectionHandler.getStatus();
      try {
        this.connectionHandler.setStatus(ConnectionStatus.COPY_IN);
        // Loop here until COPY_IN mode has finished.
        while (this.connectionHandler.getStatus() == ConnectionStatus.COPY_IN) {
          this.connectionHandler.handleMessages();
          if (Thread.interrupted()) {
            throw PGExceptionFactory.newQueryCancelledException();
          }
        }
        // Return CommandComplete if the COPY succeeded. This should not be cached until a flush.
        // Note that if an error occurred during the COPY, the message handler will automatically
        // respond with an ErrorResponse. That is why we do not check for COPY_FAILED here, and do
        // not return an ErrorResponse.
        if (connectionHandler.getStatus() == ConnectionStatus.COPY_DONE) {
          new CommandCompleteResponse(
                  this.connectionHandler.getConnectionMetadata().getOutputStream(),
                  "COPY " + copyStatement.getUpdateCount(ResultNotReadyBehavior.BLOCK))
              .send();
        }
        // Throw an exception if the COPY failed. This ensures that the BackendConnection receives
        // an error and marks the current (implicit) transaction as aborted.
        if (copyStatement.hasException(ResultNotReadyBehavior.BLOCK)
            || this.connectionHandler.getStatus() == ConnectionStatus.COPY_FAILED) {
          if (copyStatement.hasException(ResultNotReadyBehavior.BLOCK)) {
            throw copyStatement.getException();
          } else {
            throw SpannerExceptionFactory.newSpannerException(
                ErrorCode.INTERNAL, "Copy failed with unknown reason");
          }
        }
      } finally {
        this.connectionHandler.clearActiveCopyStatement();
        this.copyStatement.close();
        this.connectionHandler.setStatus(initialConnectionStatus);
      }
    }
  }
}
