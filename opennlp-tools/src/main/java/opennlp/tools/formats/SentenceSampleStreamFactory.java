/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.formats;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.params.LanguageFormatParams;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.sentdetect.SentenceSampleStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

import java.io.FileInputStream;

/**
 * Factory producing OpenNLP {@link SentenceSampleStream}s.
 */
public class SentenceSampleStreamFactory extends LanguageSampleStreamFactory<SentenceSample> {

  interface Parameters extends LanguageFormatParams {
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(SentenceSample.class,
        StreamFactoryRegistry.DEFAULT_FORMAT, new SentenceSampleStreamFactory(Parameters.class));
  }

  protected <P> SentenceSampleStreamFactory(Class<P> params) {
    super(params);
  }

  public ObjectStream<SentenceSample> create(String[] args) {
    Parameters params = ArgumentParser.parse(args, Parameters.class);

    language = params.getLang();

    CmdLineUtil.checkInputFile("Data", params.getData());
    FileInputStream sampleDataIn = CmdLineUtil.openInFile(params.getData());

    ObjectStream<String> lineStream = new PlainTextByLineStream(sampleDataIn.getChannel(),
        params.getEncoding());

    return new SentenceSampleStream(lineStream);
  }
}