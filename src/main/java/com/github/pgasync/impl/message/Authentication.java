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

package com.github.pgasync.impl.message;

/**
 * @author  Antti Laisi
 */
public class Authentication implements Message {

    byte[] md5salt;
    boolean success;

    public void setAuthenticationOk() {
        success = true;
    }

    public void setMd5Salt(byte[] md5salt) {
        this.md5salt = md5salt;
    }

    public byte[] getMd5Salt() {
        return md5salt;
    }

    public boolean isAuthenticationOk() {
        return success;
    }
}
