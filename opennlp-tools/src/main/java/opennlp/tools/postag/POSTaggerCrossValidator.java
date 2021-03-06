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

package opennlp.tools.postag;

import java.io.IOException;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.CrossValidationPartitioner;
import opennlp.tools.util.eval.Mean;
import opennlp.tools.util.model.ModelType;
import opennlp.tools.util.model.ModelUtil;

public class POSTaggerCrossValidator {

  private final String languageCode;
  
  private final TrainingParameters params;
  
  private POSDictionary tagDictionary;
  private Dictionary ngramDictionary;
  private Integer ngramCutoff;

  private Mean wordAccuracy = new Mean();
  private POSTaggerEvaluationMonitor[] listeners;

  /* this will be used to load the factory after the ngram dictionary was created */
  private String factoryClassName;
  /* user can also send a ready to use factory */
  private POSTaggerFactory factory;
  
  /**
   * Creates a {@link POSTaggerCrossValidator} that builds a ngram dictionary
   * dynamically. It instantiates a sub-class of {@link POSTaggerFactory} using
   * the tag and the ngram dictionaries.
   */
  public POSTaggerCrossValidator(String languageCode,
      TrainingParameters trainParam, POSDictionary tagDictionary,
      Integer ngramCutoff, String factoryClass,
      POSTaggerEvaluationMonitor... listeners) {
    this.languageCode = languageCode;
    this.params = trainParam;
    this.tagDictionary = tagDictionary;
    this.ngramCutoff = ngramCutoff;
    this.listeners = listeners;
    this.factoryClassName = factoryClass;
    this.ngramDictionary = null;
  }

  /**
   * Creates a {@link POSTaggerCrossValidator} using the given
   * {@link POSTaggerFactory}.
   */
  public POSTaggerCrossValidator(String languageCode,
      TrainingParameters trainParam, POSTaggerFactory factory,
      POSTaggerEvaluationMonitor... listeners) {
    this.languageCode = languageCode;
    this.params = trainParam;
    this.listeners = listeners;
    this.factory = factory;
    this.tagDictionary = null;
    this.ngramDictionary = null;
    this.ngramCutoff = null;
  }
  
  /**
   * @deprecated use
   *             {@link #POSTaggerCrossValidator(String, TrainingParameters, POSTaggerFactory, POSTaggerEvaluationMonitor...)}
   *             instead and pass in a {@link TrainingParameters} object and a
   *             {@link POSTaggerFactory}.
   */
  public POSTaggerCrossValidator(String languageCode, ModelType modelType, POSDictionary tagDictionary,
      Dictionary ngramDictionary, int cutoff, int iterations) {
    this(languageCode, create(modelType, cutoff, iterations), create(ngramDictionary, tagDictionary));
  }
  
  /**
   * @deprecated use
   *             {@link #POSTaggerCrossValidator(String, TrainingParameters, POSTaggerFactory, POSTaggerEvaluationMonitor...)}
   *             instead and pass in a {@link TrainingParameters} object and a
   *             {@link POSTaggerFactory}.
   */
  public POSTaggerCrossValidator(String languageCode, ModelType modelType, POSDictionary tagDictionary,
      Dictionary ngramDictionary) {
    this(languageCode, create(modelType, 5, 100), create(ngramDictionary, tagDictionary));
  }

  /**
   * @deprecated use
   *             {@link #POSTaggerCrossValidator(String, TrainingParameters, POSTaggerFactory, POSTaggerEvaluationMonitor...)}
   *             instead and pass in a {@link POSTaggerFactory}.
   */
  public POSTaggerCrossValidator(String languageCode,
      TrainingParameters trainParam, POSDictionary tagDictionary,
      POSTaggerEvaluationMonitor... listeners) {
    this(languageCode, trainParam, create(null, tagDictionary), listeners);
  }
  
  /**
   * @deprecated use
   *             {@link #POSTaggerCrossValidator(String, TrainingParameters, POSDictionary, Integer, String, POSTaggerEvaluationMonitor...)}
   *             instead and pass in the name of {@link POSTaggerFactory}
   *             sub-class.
   */
  public POSTaggerCrossValidator(String languageCode,
      TrainingParameters trainParam, POSDictionary tagDictionary,
      Integer ngramCutoff, POSTaggerEvaluationMonitor... listeners) {
    this(languageCode, trainParam, tagDictionary, ngramCutoff,
        POSTaggerFactory.class.getCanonicalName(), listeners);
  }

  /**
   * @deprecated use
   *             {@link #POSTaggerCrossValidator(String, TrainingParameters, POSTaggerFactory, POSTaggerEvaluationMonitor...)}
   *             instead and pass in a {@link POSTaggerFactory}.
   */
  public POSTaggerCrossValidator(String languageCode,
      TrainingParameters trainParam, POSDictionary tagDictionary,
      Dictionary ngramDictionary, POSTaggerEvaluationMonitor... listeners) {
    this(languageCode, trainParam, create(ngramDictionary, tagDictionary), listeners);
  }
  
  /**
   * Starts the evaluation.
   * 
   * @param samples
   *          the data to train and test
   * @param nFolds
   *          number of folds
   * 
   * @throws IOException
   */
  public void evaluate(ObjectStream<POSSample> samples, int nFolds) throws IOException {
    
    CrossValidationPartitioner<POSSample> partitioner = new CrossValidationPartitioner<POSSample>(
        samples, nFolds);

    while (partitioner.hasNext()) {

      CrossValidationPartitioner.TrainingSampleStream<POSSample> trainingSampleStream = partitioner
          .next();
      
      Dictionary ngramDict = null;
      if (this.ngramDictionary == null) {
        if(this.ngramCutoff != null) {
          System.err.print("Building ngram dictionary ... ");
          ngramDict = POSTaggerME.buildNGramDictionary(trainingSampleStream,
              this.ngramCutoff);
          trainingSampleStream.reset();
          System.err.println("done");
        }
      } else {
        ngramDict = this.ngramDictionary;
      }
      
      if (this.factory == null) {
        this.factory = POSTaggerFactory.create(this.factoryClassName,
            ngramDict, tagDictionary);
      }
      
      POSModel model = POSTaggerME.train(languageCode, trainingSampleStream,
          params, this.factory);

      POSEvaluator evaluator = new POSEvaluator(new POSTaggerME(model), listeners);
      
      evaluator.evaluate(trainingSampleStream.getTestSampleStream());

      wordAccuracy.add(evaluator.getWordAccuracy(), evaluator.getWordCount());
    }
  }
  
  /**
   * Retrieves the accuracy for all iterations.
   * 
   * @return the word accuracy
   */
  public double getWordAccuracy() {
    return wordAccuracy.mean();
  }
  
  /**
   * Retrieves the number of words which where validated
   * over all iterations. The result is the amount of folds
   * multiplied by the total number of words.
   * 
   * @return the word count
   */
  public long getWordCount() {
    return wordAccuracy.count();
  }
  
  private static TrainingParameters create(ModelType type, int cutoff, int iterations) {
    TrainingParameters params = ModelUtil.createTrainingParameters(iterations, cutoff);
    params.put(TrainingParameters.ALGORITHM_PARAM, type.toString());
    return params;
  }
  
  private static POSTaggerFactory create(Dictionary ngram, POSDictionary pos) {
    return new POSTaggerFactory(ngram, pos);
  }
}
