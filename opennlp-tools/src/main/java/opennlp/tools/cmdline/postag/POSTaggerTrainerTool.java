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

package opennlp.tools.cmdline.postag;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import opennlp.model.TrainUtil;
import opennlp.tools.cmdline.AbstractTrainerTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.TrainingToolParams;
import opennlp.tools.cmdline.postag.POSTaggerTrainerTool.TrainerToolParams;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.postag.POSDictionary;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerFactory;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelType;
import opennlp.tools.util.model.ModelUtil;

public final class POSTaggerTrainerTool
    extends AbstractTrainerTool<POSSample, TrainerToolParams> {
  
  interface TrainerToolParams extends TrainingParams, TrainingToolParams {
  }

  public POSTaggerTrainerTool() {
    super(POSSample.class, TrainerToolParams.class);
  }

  public String getShortDescription() {
    return "trains a model for the part-of-speech tagger";
  }
  
  public void run(String format, String[] args) {
    super.run(format, args);

    mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), true);
    if (mlParams != null && !TrainUtil.isValid(mlParams.getSettings())) {
      throw new TerminateToolException(1, "Training parameters file is invalid!");
    }

    if(mlParams == null) {
      mlParams = ModelUtil.createTrainingParameters(params.getIterations(), params.getCutoff());
      mlParams.put(TrainingParameters.ALGORITHM_PARAM, getModelType(params.getType()).toString());
    }

    File modelOutFile = params.getModel();
    CmdLineUtil.checkOutputFile("pos tagger model", modelOutFile);

    Dictionary ngramDict = null;
    
    Integer ngramCutoff = params.getNgram();
    
    if (ngramCutoff != null) {
      System.err.print("Building ngram dictionary ... ");
      try {
        ngramDict = POSTaggerME.buildNGramDictionary(sampleStream, ngramCutoff);
        sampleStream.reset();
      } catch (IOException e) {
        throw new TerminateToolException(-1,
            "IO error while building NGram Dictionary: " + e.getMessage());
      }
      System.err.println("done");
    }

    // TODO: Move to util method ...
    POSDictionary tagdict = null;
    if (params.getDict() != null) {
      try {
        tagdict = POSDictionary.create(new FileInputStream(params.getDict()));
      } catch (IOException e) {
        throw new TerminateToolException(-1,
            "IO error while loading POS Dictionary: " + e.getMessage());
      }
    }

    POSTaggerFactory postaggerFactory = null;
    try {
      postaggerFactory = POSTaggerFactory.create(params.getFactory(),
          ngramDict, tagdict);
    } catch (InvalidFormatException e) {
      throw new TerminateToolException(-1, e.getMessage());
    }
    
    POSModel model;
    try {
      model = opennlp.tools.postag.POSTaggerME.train(factory.getLang(),
          sampleStream, mlParams, postaggerFactory);
    }
    catch (IOException e) {
      throw new TerminateToolException(-1, "IO error while reading training data or indexing data: " + e.getMessage());
    }
    finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }
    
    CmdLineUtil.writeModel("pos tagger", modelOutFile, model);
  }
  
  static ModelType getModelType(String modelString) {
    ModelType model;
    if (modelString == null)
      modelString = "maxent";
    
    if (modelString.equals("maxent")) {
      model = ModelType.MAXENT; 
    }
    else if (modelString.equals("perceptron")) {
      model = ModelType.PERCEPTRON; 
    }
    else if (modelString.equals("perceptron_sequence")) {
      model = ModelType.PERCEPTRON_SEQUENCE; 
    }
    else {
      model = null;
    }
    return model;
  }
}
