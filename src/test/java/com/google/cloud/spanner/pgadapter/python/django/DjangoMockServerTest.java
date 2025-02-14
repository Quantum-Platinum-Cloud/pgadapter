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

package com.google.cloud.spanner.pgadapter.python.django;

import com.google.cloud.spanner.MockSpannerServiceImpl;
import com.google.cloud.spanner.pgadapter.AbstractMockServerTest;
import java.util.Arrays;
import org.junit.BeforeClass;

public class DjangoMockServerTest extends AbstractMockServerTest {

  @BeforeClass
  public static void startMockSpannerAndPgAdapterServers() throws Exception {
    // Django needs postgres version to be greater than or equal to 11
    doStartMockSpannerAndPgAdapterServers(
        new MockSpannerServiceImpl(), "d", Arrays.asList("--server-version", "11.1"));
  }
}
