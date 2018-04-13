package edu.sentise;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Random;

import javax.management.RuntimeErrorException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import edu.sentise.factory.BasePOSUtility;
import edu.sentise.factory.BasicFactory;
import edu.sentise.model.SentimentData;
import edu.sentise.preprocessing.AncronymHandler;
import edu.sentise.preprocessing.BiGramTriGramHandler;
import edu.sentise.preprocessing.ContractionLoader;

import edu.sentise.preprocessing.EmoticonProcessor;
import edu.sentise.preprocessing.ExclamationHandler;
import edu.sentise.preprocessing.MyStopWordsHandler;
import edu.sentise.preprocessing.POSTagProcessor;
import edu.sentise.preprocessing.QuestionMarkHandler;
import edu.sentise.preprocessing.StanfordCoreNLPLemmatizer;
import edu.sentise.preprocessing.TextPreprocessor;
import edu.sentise.preprocessing.URLRemover;
import edu.sentise.test.ARFFTestGenerator;
import edu.sentise.util.Constants;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import weka.core.stemmers.NullStemmer;
import weka.core.stemmers.SnowballStemmer;
import weka.core.tokenizers.WordTokenizer;
import weka.filters.Filter;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.unsupervised.attribute.StringToWordVector;

public class SentiSE {

	private HashMap<Integer, Integer> classMapping;
	private Classifier classifier;
	private String emoticonDictionary = Constants.EMOTICONS_FILE_NAME;
	private String stopWordDictionary = Constants.STOPWORDS_FILE_NAME;
	private String contractionDictionary = Constants.CONTRACTION_TEXT_FILE_NAME;
	private String oracleFileName = Constants.ORACLE_FILE_NAME;
	private String acronymDictionary = Constants.ACRONYM_WORD_FILE;
	private int minTermFrequeny = 3;
	private int maxWordsToKeep = 2500;
	private String algorithm = "RF";

	private boolean crossValidate = false;
	private boolean forceRcreateTrainingData = false;
	private boolean applyPosTag = false; // Apply POS tags with words
	private boolean keepOnlyImportantPos = false; // keepOnlyImportantPos means keeping only verbs,adjectives and
													// adverbs
	private boolean preprocessNegation = false; // preprocessNegation means handle the negation effects on other POS
	private boolean applyContextTag = false; // Apply context information of a word like
											// VP,ADVP or NP
	private int addSentiScoreType = 0; // if a sentence contains sentiment word. Add a correspponding string with it.
	private boolean processQuestionMark = false; // process question and exclamatory marks
	private boolean processExclamationMark = false;
	private boolean handleNGram = false;

	private boolean useStemmer = false;
	private boolean useLemmatizer = false;

	Instances trainingInstances = null;

	public void setEmoticonDictionary(String emoticonDictionary) {
		this.emoticonDictionary = emoticonDictionary;
	}

	public void setStopWordDictionary(String stopWordDictionary) {
		this.stopWordDictionary = stopWordDictionary;
	}

	public void setContractionDictionary(String contractionDictionary) {
		this.contractionDictionary = contractionDictionary;
	}

	public void setOracleFileName(String oracleFileName) {
		this.oracleFileName = oracleFileName;
	}

	public String getAlgorithm() {
		return algorithm;
	}

	public void setAlgorithm(String algorithm) {
		this.algorithm = algorithm;
	}

	public int getMinTermFrequeny() {
		return minTermFrequeny;
	}

	public void setMinTermFrequeny(int minTermFrequeny) {
		this.minTermFrequeny = minTermFrequeny;
	}

	public int getMaxWordsToKeep() {
		return maxWordsToKeep;
	}

	public void setMaxWordsToKeep(int maxWordsToKeep) {
		this.maxWordsToKeep = maxWordsToKeep;
	}

	public void setPreprocessNegation(boolean preprocessNegation) {
		this.preprocessNegation = preprocessNegation;
	}

	public boolean isCrossValidate() {
		return crossValidate;
	}

	public void setCrossValidate(boolean crossValidate) {
		this.crossValidate = crossValidate;
	}

	public boolean isForceRcreateTrainingData() {
		return forceRcreateTrainingData;
	}

	public void setForceRcreateTrainingData(boolean forceRcreateTrainingData) {
		this.forceRcreateTrainingData = forceRcreateTrainingData;
	}

	public void setKeepPosTag(boolean keep) {
		applyPosTag = keep;
	}

	private ArrayList<TextPreprocessor> preprocessPipeline = new ArrayList<TextPreprocessor>();

	public SentiSE() {

		// common preprocessing steps, always applied
		preprocessPipeline.add(new EmoticonProcessor(this.emoticonDictionary));
		preprocessPipeline.add(new ContractionLoader(this.contractionDictionary));		
		preprocessPipeline.add(new URLRemover());
		preprocessPipeline.add(new AncronymHandler(this.acronymDictionary));

	}

	public void generateTrainingInstance(boolean oversample) throws Exception {

		System.out.println("Reading oracle file...");
		ArrayList<SentimentData> sentimentDataList = SentimentData.parseSentimentData(Constants.ORACLE_FILE_NAME);

		if (this.processExclamationMark)
			preprocessPipeline.add(new ExclamationHandler());

		if (this.processQuestionMark)
			preprocessPipeline.add(new QuestionMarkHandler());
		if (this.handleNGram)
			preprocessPipeline.add(new BiGramTriGramHandler());

		System.out.println("Preprocessing text ..");
		preprocessPipeline
				.add(new POSTagProcessor(BasicFactory.getPOSUtility(applyPosTag, keepOnlyImportantPos, applyContextTag),
						this.preprocessNegation, addSentiScoreType));

		for (TextPreprocessor process : preprocessPipeline) {
			sentimentDataList = process.apply(sentimentDataList);
		}

		System.out.println("Converting to WEKA format ..");
		Instances rawInstance = ARFFTestGenerator.generateTestData(sentimentDataList);

		System.out.println("Converting string to vector..");
		this.trainingInstances = generateFilteredInstance(rawInstance, true);

		this.trainingInstances.setClassIndex(0);

		storeAsARFF(this.trainingInstances, this.oracleFileName + ".arff");

	}

	private void storeAsARFF(Instances instance, String fileName) {

		ARFFTestGenerator.writeInFile(this.trainingInstances, fileName);
		System.out.println("Instance saved as:" + fileName);
	}

	private Instances loadInstanceFromARFF(String arffFileName) throws Exception {
		DataSource dataSource = new DataSource(arffFileName);
		Instances loadedInstance = dataSource.getDataSet();
		loadedInstance.setClassIndex(0);
		System.out.println("Instance loaded from:" + arffFileName);
		return loadedInstance;
	}

	public void reloadClassifier() throws Exception {

		this.generateTrainingInstance(true);
		// trainingInstances = applyOversampling(trainingInstances);
		System.out.println("Training classifier..");
		this.classifier = WekaClassifierBuilder.createClassifierFromInstance(this.algorithm, this.trainingInstances);
		WekaClassifierBuilder.storeClassfierModel("models/" + this.algorithm + "." + this.oracleFileName + ".model",
				this.classifier);

	}

	// public Instances applyOversampling(Instances filteredInstance) throws
	// Exception {
	// int count[] = filteredInstance.attributeStats(0).nominalCounts;
	// System.out.println("Instances 0->" + count[0] + ", -1->" + count[1] + ", 1->"
	// + count[2]);
	//
	// System.out.println("Creating synthetic negative samples");
	// SMOTE oversampler = new SMOTE();
	// oversampler.setNearestNeighbors(15);
	// oversampler.setClassValue("2");
	//
	// oversampler.setInputFormat(filteredInstance);
	// filteredInstance = Filter.useFilter(filteredInstance, oversampler);
	// System.out.println("Creating synthetic positive samples");
	// SMOTE oversampler2 = new SMOTE();
	// oversampler2.setClassValue("3");
	// oversampler2.setNearestNeighbors(15);
	// oversampler2.setPercentage(40);
	// oversampler2.setInputFormat(filteredInstance);
	//
	// filteredInstance = Filter.useFilter(filteredInstance, oversampler2);
	// System.out.println("Finished oversampling..");
	// return filteredInstance;
	//
	// }

	public int[] getSentimentScore(ArrayList<String> sentences) throws Exception {

		ArrayList<String> sentiText = new ArrayList<String>();
		for (int i = 0; i < sentences.size(); i++) {
			sentiText.add(preprocessText(sentences.get(i)));
		}

		int[] computedScores = new int[sentences.size()];

		Instances testInstances = generateInstanceFromList(sentiText);

		for (int j = 0; j < testInstances.size(); j++) {

			computedScores[j] = classMapping.get((int) classifier.classifyInstance(testInstances.get(j)));

		}
		return computedScores;
	}

	private String preprocessText(String text) {
		// text = contractionHandler.preprocessContractions(text);
		// text = URLRemover.removeURL(text);
		// text = emoticonHandler.preprocessEmoticons(text);
		// text = ParserUtility.preprocessPOStags(text);
		return text;
	}

	private Instances generateInstanceFromList(ArrayList<String> sentiText) throws Exception {
		Instances instance = ARFFTestGenerator.generateTestDataFromString(sentiText);
		return generateFilteredInstance(instance, false);

	}

	private Instances generateFilteredInstance(Instances instance, boolean disardLowFreqTerms) throws Exception {
		StringToWordVector filter = new StringToWordVector();
		filter.setInputFormat(instance);
		WordTokenizer customTokenizer = new WordTokenizer();
		customTokenizer.setDelimiters(Constants.DELIMITERS);
		filter.setTokenizer(customTokenizer);
		filter.setStopwordsHandler(new MyStopWordsHandler());

		if (this.useStemmer) {
			filter.setStemmer(new SnowballStemmer());
		} else if (this.useLemmatizer) {
			StanfordCoreNLPLemmatizer lemmatizer = new StanfordCoreNLPLemmatizer();
			filter.setStemmer(lemmatizer);
		} else
			filter.setStemmer(new NullStemmer());

		filter.setLowerCaseTokens(true);
		filter.setTFTransform(true);
		filter.setIDFTransform(true);
		if (disardLowFreqTerms) {
			filter.setMinTermFreq(this.minTermFrequeny);
			filter.setWordsToKeep(this.maxWordsToKeep);
		}

		return Filter.useFilter(instance, filter);

	}

	private void tenFoldCV() {

		try {

			String arffFileName = this.oracleFileName + ".arff";
			File arffFile = new File(arffFileName);

			if (!arffFile.exists() || this.isForceRcreateTrainingData()) {
				this.generateTrainingInstance(false);
			} else {
				this.trainingInstances = loadInstanceFromARFF(arffFileName);

			}
			int folds = 10;

			Random rand = new Random(System.currentTimeMillis());
			Instances randData = new Instances(this.trainingInstances);
			randData.randomize(rand);

			double pos_precision[] = new double[folds];
			double neg_precision[] = new double[folds];
			double neu_precision[] = new double[folds];

			double pos_recall[] = new double[folds];
			double neg_recall[] = new double[folds];
			double neu_recall[] = new double[folds];

			double pos_fscore[] = new double[folds];
			double neg_fscore[] = new double[folds];
			double neu_fscore[] = new double[folds];

			double accuracies[] = new double[folds];
			double kappa[] = new double[folds];

			// perform cross-validation
			Evaluation eval = new Evaluation(randData);
			for (int n = 0; n < folds; n++) {
				System.out.println(".............................");
				System.out.println(".......Testing on Fold:" + n);
				System.out.println("..........................");
				File oracleFile = new File(this.oracleFileName);

				Instances train = null, test = null;

				train = randData.trainCV(folds, n);
				test = randData.testCV(folds, n);

				Classifier clsCopy = WekaClassifierBuilder.getClassifierForAlgorithm(this.algorithm);
				System.out.println("Training classifier model..");
				clsCopy.buildClassifier(train);
				eval.evaluateModel(clsCopy, test);

				accuracies[n] = eval.pctCorrect();

				neu_precision[n] = eval.precision(0);
				neg_precision[n] = eval.precision(1);
				pos_precision[n] = eval.precision(2);

				neu_fscore[n] = eval.fMeasure(0);
				neg_fscore[n] = eval.fMeasure(1);
				pos_fscore[n] = eval.fMeasure(2);

				neu_recall[n] = eval.recall(0);
				neg_recall[n] = eval.recall(1);
				pos_recall[n] = eval.recall(2);
				kappa[n] = eval.kappa();

				System.out.println("Accuracy:" + eval.pctCorrect());
				System.out.println("Kappa:" + eval.kappa());

				System.out.println(" Precision(neutral):" + eval.precision(0));
				System.out.println("Recall(neutral):" + eval.recall(0));
				System.out.println("Fmeasure(neutral):" + eval.fMeasure(0));

				System.out.println(" Precision(negative):" + eval.precision(1));
				System.out.println("Recall(negative):" + eval.recall(1));
				System.out.println("Fmeasure(negative):" + eval.fMeasure(1));

				System.out.println(" Precision(positive):" + eval.precision(2));
				System.out.println("Recall(positive):" + eval.recall(2));
				System.out.println("Fmeasure(positive):" + eval.fMeasure(2));

			}

			System.out.println("\n\n.......Average......: ");
			System.out.println("Accuracy:" + getAverage(accuracies));

			System.out.println("Algorithm:" + this.algorithm + "\n Oracle:" + this.oracleFileName);
			System.out.println("Precision (Neutral):" + getAverage(neu_precision));
			System.out.println("Recall (Neutral):" + getAverage(neu_recall));
			System.out.println("F-Measure (Neutral):" + getAverage(neu_fscore));

			System.out.println("Precision (Negative):" + getAverage(neg_precision));
			System.out.println("Recall (Negative):" + getAverage(neg_recall));
			System.out.println("F-measure (Negative):" + getAverage(neg_fscore));

			System.out.println("Precision (Positive):" + getAverage(pos_precision));
			System.out.println("Recall (Positive):" + getAverage(pos_recall));
			System.out.println("F-Measure (Positive):" + getAverage(pos_fscore));

			System.out.println("Kappa: " + getAverage(kappa));
			
			System.out.println("\n\n.......Configuration......: ");
			System.out.println("Algorithm: "+this.algorithm);
			System.out.println("Use ngram: "+this.handleNGram);
			System.out.println("Negation preprocess: "+this.preprocessNegation);
			System.out.println("Context tag: "+this.applyContextTag);
			System.out.println("POS tag: "+this.applyPosTag);
			System.out.println("Replace question mark: "+this.processQuestionMark);
			System.out.println("Replace question mark: "+this.processExclamationMark);
			System.out.println("Stemming:"+this.useStemmer);
			System.out.println("Lemmatization:"+this.useLemmatizer);
			System.out.println("Only V, Adv, Adj:"+this.keepOnlyImportantPos);
			System.out.println("Mark sentiment words:"+this.addSentiScoreType);
			

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private double getAverage(double[] elements) {
		double sum = 0.0;
		for (int i = 0; i < elements.length; i++)
			sum = sum + elements[i];

		// calculate average value
		double average = sum / elements.length;
		return average;
	}

	public static void main(String[] args) {

		SentiSE instance = new SentiSE();
		if (!instance.isCommandLineParsed(args))
			return;
		// if (args.length > 0)
		// instance.setAlgorithm(args[0].trim());

		try {
			instance.setForceRcreateTrainingData(true);
			instance.tenFoldCV();

		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

	}

	private boolean isCommandLineParsed(String[] args) {
		CommandLineParser commandLineParser = new DefaultParser();

		Options options = new Options();

		options.addOption(Option.builder("algo").hasArg(true)
				.desc("Algorithm for classifier. \nChoices are: RF(Default)| DT | NB").build());
		options.addOption(Option.builder("help").hasArg(false).desc("Prints help message").build());
		options.addOption(Option.builder("root").hasArg(true)
				.desc("Word root determination process.\n 0=None (Default) | 1=Stemming | 2=Lemmatization ").build());
		options.addOption(Option.builder("negate").hasArg(false)
				.desc("Prefix words in negative context\n Default: False").build());
		options.addOption(Option.builder("tag").hasArg(true)
				.desc("Add tags to words.\n0=None (Default)| 1= POS | 2=Context ").build());
		options.addOption(Option.builder("ngram").hasArg(false).desc("Use ngrams. Default: False").build());
		options.addOption(Option.builder("features").hasArg(true)
				.desc("Features to use.\n 1 = All (default) | 2 = Only Verbs, Adverbs, and Adjectives").build());
		options.addOption(Option.builder("punctuation").hasArg(true)
				.desc("Preprocess punctuations.\n 0= None (default) | 1= Question | 2= Exclamation | 3=Both ").build());
		options.addOption(Option.builder("sentiword").hasArg(true)
				.desc("Categorize sentiment words.\n 0= None (default) | 2= Two groups |4= Four groups ").build());

		Option termFreq = Option.builder("minfreq").hasArg()
				.desc("Minimum frequecy required to be considered as a feature. Default: 5").build();
		termFreq.setType(Number.class);
		options.addOption(termFreq);

		Option maxterms = Option.builder("maxfeatures").hasArg().desc("Maximum number of features. Default: 2500").build();
		termFreq.setType(Number.class);
		options.addOption(maxterms);

		try {
			CommandLine commandLine = commandLineParser.parse(options, args);
			HelpFormatter formatter = new HelpFormatter();
			if (commandLine.hasOption("help")) {

				printUsageAndExit(options, formatter);
			}

			if (commandLine.hasOption("algo")) {
				String algo = commandLine.getOptionValue("algo");
				if (algo.equals("RF") || algo.equals("DT") || algo.equals("NB") || algo.equals("RF")
						|| algo.equals("RNN"))
					this.algorithm = algo;
				else
					printUsageAndExit(options, formatter);
			}

			if (commandLine.hasOption("root")) {
				if (commandLine.getOptionValue("root").equals("1")) {
					useStemmer = true;
					useLemmatizer = false;
				} else if (commandLine.getOptionValue("root").equals("2")) {
					useStemmer = false;
					useLemmatizer = true;
				} else {
					useStemmer = false;
					useLemmatizer = false;
				}

			}

			if (commandLine.hasOption("negate")) {
				setPreprocessNegation(true);
			}

			if (commandLine.hasOption("tag")) {
				if (commandLine.getOptionValue("tag").equals("1")) {
					applyPosTag = true;
					applyContextTag = false;
				}

				else if (commandLine.getOptionValue("tag").equals("2")) {
					applyPosTag = false;
					applyContextTag = true;
				} else {
					applyPosTag = false;
					applyContextTag = false;
				}

			}

			if (commandLine.hasOption("punctuation")) {
				if (commandLine.getOptionValue("punctuation").equals("1")) {
					processQuestionMark = true;
					processExclamationMark = false;
				} else if (commandLine.getOptionValue("punctuation").equals("2")) {
					processQuestionMark = false;
					processExclamationMark = true;
				} else if (commandLine.getOptionValue("punctuation").equals("3")) {
					processQuestionMark = true;
					processExclamationMark = true;
				} else {
					processQuestionMark = false;
					processExclamationMark = false;
				}

			}

			if (commandLine.hasOption("features")) {
				if (commandLine.getOptionValue("features").equals("2"))
					keepOnlyImportantPos = true;
				else
					keepOnlyImportantPos = false;

			}
			if (commandLine.hasOption("sentiword")) {
				if (commandLine.getOptionValue("sentiword").equals("0"))
					addSentiScoreType = 0;
				else if (commandLine.getOptionValue("sentiword").equals("2"))
					addSentiScoreType = 2;
				else if (commandLine.getOptionValue("sentiword").equals("4"))
					addSentiScoreType = 4;

			}
			
			if (commandLine.hasOption("ngram")) {

				handleNGram = true;
			}
			
			if (commandLine.hasOption("minfreq")) {

				this.minTermFrequeny=Integer.parseInt(commandLine.getOptionValue("minfreq"));
			}
			
			if (commandLine.hasOption("maxfeatures")) {

				this.maxWordsToKeep=Integer.parseInt(commandLine.getOptionValue("maxfeatures"));
			}

		} catch (ParseException e) {
			e.printStackTrace();

		}
		return true;
	}

	private void printUsageAndExit(Options options, HelpFormatter formatter) {
		formatter.printHelp("sentise", options, true);
		System.exit(0);
	}

}
