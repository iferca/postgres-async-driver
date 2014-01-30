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

package com.github.pgasync;

import java.util.List;

import com.github.pgasync.callback.ErrorHandler;
import com.github.pgasync.callback.ResultHandler;
import com.github.pgasync.callback.TransactionHandler;

public interface Connection {

	void query(String sql, ResultHandler onResult, ErrorHandler onError);

	@SuppressWarnings("rawtypes")
	void query(String sql, List/*<Object>*/ params, ResultHandler onResult, ErrorHandler onError);

	void begin(TransactionHandler onTransaction, ErrorHandler onError);

	void close();

}
