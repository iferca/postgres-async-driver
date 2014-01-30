/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.pgasync.impl;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.pgasync.Connection;
import com.github.pgasync.ResultSet;
import com.github.pgasync.SqlException;
import com.github.pgasync.Transaction;
import com.github.pgasync.callback.ConnectionHandler;
import com.github.pgasync.callback.ErrorHandler;
import com.github.pgasync.callback.ResultHandler;
import com.github.pgasync.callback.TransactionCompletedHandler;
import com.github.pgasync.callback.TransactionHandler;
import com.github.pgasync.impl.message.Authentication;
import com.github.pgasync.impl.message.Bind;
import com.github.pgasync.impl.message.CommandComplete;
import com.github.pgasync.impl.message.DataRow;
import com.github.pgasync.impl.message.ErrorResponse;
import com.github.pgasync.impl.message.ExtendedQuery;
import com.github.pgasync.impl.message.Parse;
import com.github.pgasync.impl.message.PasswordMessage;
import com.github.pgasync.impl.message.Query;
import com.github.pgasync.impl.message.ReadyForQuery;
import com.github.pgasync.impl.message.RowDescription;
import com.github.pgasync.impl.message.Startup;


public class PgConnection implements Connection, PgProtocolCallbacks {

	final PgProtocolStream stream;

	boolean connected;
	String username;
	String password;

	ConnectionHandler connectedHandler;
	ErrorHandler errorHandler;
	ResultHandler queryHandler;
	PgResultSet resultSet;

	public PgConnection(PgProtocolStream stream) {
		this.stream = stream;
	}

	public void connect(String username, String password, String database, ConnectionHandler onConnected, ErrorHandler onError) {
		this.username = username;
		this.password = password;
		this.errorHandler = onError;
		this.connectedHandler = onConnected;
		stream.connect(new Startup(username, database), this);
	}

	@Override
	public void close() {
		stream.close();
	}

	// Connection

	@Override
	public void query(String sql, ResultHandler onQuery, ErrorHandler onError) {
		if(queryHandler != null) {
			onError.onError(new IllegalStateException("Query already in progress"));
			return;
		}
		errorHandler = onError;
		queryHandler = onQuery;
		stream.send(new Query(sql));
	}

	@Override
	@SuppressWarnings({"unchecked","rawtypes"})
	public void query(String sql, List params, ResultHandler onQuery, ErrorHandler onError) {
		if(params == null || params.isEmpty()) {
			query(sql, onQuery, onError);
			return;
		}
		if(queryHandler != null) {
			onError.onError(new IllegalStateException("Query already in progress"));
			return;
		}
		errorHandler = onError;
		queryHandler = onQuery;
		stream.send(new Parse(sql), new Bind(params), ExtendedQuery.DESCRIBE, ExtendedQuery.EXECUTE, ExtendedQuery.SYNC);
	}

	@Override
	public void begin(final TransactionHandler handler, final ErrorHandler onError) {

		final TransactionCompletedHandler[] onCompletedRef = new TransactionCompletedHandler[1];
		final ResultHandler queryToComplete = new ResultHandler() {
			@Override
			public void onResult(ResultSet rows) {
				onCompletedRef[0].onComplete();
			}
		};

		final Transaction transaction = new Transaction() {
			@Override
			public void commit(TransactionCompletedHandler completed, ErrorHandler onCommitError) {
				onCompletedRef[0] = completed;
				query("COMMIT", queryToComplete, onCommitError);
			}
			@Override
			public void rollback(TransactionCompletedHandler onCompleted, ErrorHandler onRollbackError) {
				onCompletedRef[0] = onCompleted;
				query("ROLLBACK", queryToComplete, onRollbackError);
			}
		};

		query("BEGIN", new ResultHandler() {
			@Override
			public void onResult(ResultSet ignored) {
				try {
					handler.onBegin(PgConnection.this, transaction);
				} catch(Exception e) {
					invokeOnError(onError, e);
				}
			}
		}, onError);
	}

	// PgProtocolCallbacks

	@Override
	public void onThrowable(Throwable t) {
		ErrorHandler err = errorHandler;

		errorHandler = null;
		queryHandler = null;
		resultSet = null;

		invokeOnError(err, t);
	}

	@Override
	public void onErrorResponse(ErrorResponse msg) {
		onThrowable(new SqlException(msg.getLevel(), msg.getCode(), msg.getMessage()));
	}

	@Override
	public void onAuthentication(Authentication msg) {
		if(!msg.isAuthenticationOk()) {
			stream.send(new PasswordMessage(username, password, msg.getMd5Salt()));
			username = password = null;
		}
	}

	@Override
	public void onRowDescription(RowDescription msg) {
		resultSet = new PgResultSet(msg.getColumns());
	}

	@Override
	public void onCommandComplete(CommandComplete msg) {
		if(resultSet == null) {
			resultSet = new PgResultSet();
		}
		resultSet.setUpdatedRows(msg.getUpdatedRows());
	}

	@Override
	public void onDataRow(DataRow msg) {
		resultSet.add(new PgRow(msg));
	}

	@Override
	public void onReadyForQuery(ReadyForQuery msg) {
		if(!connected) {
			onConnected();
			return;
		}
		if(queryHandler != null) {
			ResultHandler onResult = queryHandler;
			ErrorHandler onError = errorHandler;
			ResultSet result = resultSet;

			queryHandler = null;
			errorHandler = null;
			resultSet = null;

			try {
				onResult.onResult(result);
			} catch(Exception e) {
				invokeOnError(onError, e);
			}
		}
	}

	void onConnected() {
		connected = true;
		try {
			connectedHandler.onConnection(this);
		} catch (Exception e) {
			invokeOnError(errorHandler, e);
		}
		connectedHandler = null;
	}

	void invokeOnError(ErrorHandler err, Throwable t) {
		if(err != null) {
			try {
				err.onError(t);
			} catch(Exception e) {
				Logger.getLogger(getClass().getName()).log(Level.SEVERE, 
						"ErrorHandler " + err + " failed with exception", e);
			}	
		} else {
			Logger.getLogger(getClass().getName()).log(Level.SEVERE, 
					"Exception caught but no error handler is set", t);
		}
	}

}
