package com.vidura.fyp.hmm;

import matlabcontrol.*;
import matlabcontrol.extensions.MatlabTypeConverter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import com.csvreader.CsvReader;

public class HMModel {

	public static void main(String[] args) throws MatlabConnectionException, MatlabInvocationException, IOException {

		String mohName = "MC - Colombo";
		int noOfWeeksToTrain = 37; // maximum number of training weeks is 50 since we can use only 51 weeks and at least one week is needed for testing
		int sizeOfDengueInterval = 4;
		int sizeOfMobilityInterval = 2;
		int sizeOfRainFallInterval = 2;
		int maxNoOfDengueCases = 2000;
		int maxMobilityValue = 4000;
		int maxRainFallValue = 500;
		
		Double mobilityThreshold = 0.1;

		String hiddenStateSeq = "";
		String emissionSeqOfCandidateMOH = "";
		int mohId = -1;
		
		//CsvReader rainFall = null;
		ArrayList<Integer> hiddenStateTestSeq = null;
		ArrayList<Double> mobilityWithOthers = null;
		ArrayList<Double> mobilityPerHostWithOthers = null;
		ArrayList<Integer> neighbors = null;
		HashMap<Integer, String[]> trainedMatrices = null;
		HashMap<Integer, ArrayList<Integer>> dengueMobilitySequencesOfNeighborsToTrain = null;
		HashMap<Integer, ArrayList<Integer>> dengueMobilitySequencesOfNeighborsToTest = null;
		HashMap<Integer, Double> averageMobilityPerInfectedHostWithOthers = null;
		HashMap<Integer, Integer> dengueLevelDiscretizar = new HashMap<Integer, Integer>();
		HashMap<Integer, Integer> dengueMobilityDiscretizar = new HashMap<Integer, Integer>();
		HashMap<Integer, Integer> rainFallDiscretizar = new HashMap<Integer, Integer>();
		
		MatlabProxyFactory factory = new MatlabProxyFactory();
		MatlabProxy proxy = factory.getProxy();
		MatlabTypeConverter processor = new MatlabTypeConverter(proxy);

		// Initialize dengueLevelDiscretizar
		for (int i = 0, j = 1; i < maxNoOfDengueCases; i++) {
			if (i != 0 && i % sizeOfDengueInterval == 0) {
				j++;
			}
			dengueLevelDiscretizar.put(i, j);
		}

		// Initialize dengueMobilityDiscretizar
		for (int i = 0, j = 1; i < maxMobilityValue; i++) {
			if (i != 0 && i % sizeOfMobilityInterval == 0) {
				j++;
			}
			dengueMobilityDiscretizar.put(i, j);
		}
	
		// Initialize rainFallDiscretizar
		for (int i = 0, j = 1; i < maxRainFallValue; i++) {
			if (i != 0 && i % sizeOfRainFallInterval == 0) {
				j++;
			}
			rainFallDiscretizar.put(i, j);
		}
			
		
		
		//get the MOH id
		mohId = getMohId(mohName, "mohAreas.csv");
		if (mohId < 0) {
			System.out.println("MOH is not found");
			return;
		}
		
		//get the mobility values between candidate moh area and the other moh areas
		mobilityWithOthers = getMobilitiesWithOthers(mohId, "mobility.csv");
		
		//get the average mobility per host of each MOH with the candidate MOH
		mobilityPerHostWithOthers = getMobilityPerHostWithOthers(mobilityWithOthers, "resident_work_processed.csv");
		
		//get the average mobility per infected host of each MOH with the candidate MOH to define neighbors
		averageMobilityPerInfectedHostWithOthers = getAverageMobilityPerInfectedHostWithOthers(mobilityPerHostWithOthers, "dengueCases2013.csv", noOfWeeksToTrain);
		
		//get the neighbors list
		neighbors = getTheNeighborsList(averageMobilityPerInfectedHostWithOthers, "dengueCases2013.csv", mobilityThreshold);
		
		//get dengue mobility sequences for each neighbor to train the model (dengue mobility = mobility per infected host)
		dengueMobilitySequencesOfNeighborsToTrain = getDengueMobilitySequencesOfNeighborsToTrain(neighbors, mobilityPerHostWithOthers, "dengueCases2013.csv", noOfWeeksToTrain, dengueMobilityDiscretizar);
		
		//get dengue mobility sequence for each neighbor to test the model
		dengueMobilitySequencesOfNeighborsToTest = getDengueMobilitySequencesOfNeighborsToTest(neighbors, mobilityPerHostWithOthers, "dengueCases2013.csv", noOfWeeksToTrain, dengueMobilityDiscretizar);

		//get the hidden state sequence
		hiddenStateSeq = getHiddenStateSeqOfCandidateMoh(mohId, 2, "dengueCases2013.csv", noOfWeeksToTrain, dengueLevelDiscretizar);
				
		//get the hidden state test sequence
		hiddenStateTestSeq = getHiddenStateTestSeqOfCandidateMoh(mohId, "dengueCases2013.csv", noOfWeeksToTrain, dengueLevelDiscretizar);

		
		System.out.print("figure(\"" + mohName + "\"); clear();");
		System.out.print("series(\"Predicted\", \"ko:\");");

		// get transition, emission matrices and emission sequence of neighbors
		trainedMatrices = getTrainedMatrices(neighbors, dengueMobilitySequencesOfNeighborsToTrain, proxy, processor, hiddenStateSeq);
			
		//get the observed dengue sequence of candidate MOH to train
		emissionSeqOfCandidateMOH = getHiddenStateSeqOfCandidateMoh(mohId, 1, "dengueCases2013.csv", noOfWeeksToTrain - 1, dengueLevelDiscretizar);
		
		//get the observed dengue sequence of candidate MOH to test
		ArrayList<Integer> emissionSeqOfCandidateMOHtoTest = getEmissionSeqOfCandidateMOHtoTest(mohId, "dengueCases2013.csv", noOfWeeksToTrain, dengueLevelDiscretizar);
		dengueMobilitySequencesOfNeighborsToTest.put(mohId, emissionSeqOfCandidateMOHtoTest);
		
		
		//get trained matrices for emissionSeqOfCandidateMOH
		String[] tranAndEmisMatricesForEmissionSeqOfCandidateMOH = trainHMM(proxy, processor, hiddenStateSeq, emissionSeqOfCandidateMOH);
		
		String[] trainedMatricesForEmissionSeqOfCandidateMOH = new String[3];
		
		trainedMatricesForEmissionSeqOfCandidateMOH[0] = tranAndEmisMatricesForEmissionSeqOfCandidateMOH[0];
		trainedMatricesForEmissionSeqOfCandidateMOH[1] = tranAndEmisMatricesForEmissionSeqOfCandidateMOH[1];
		trainedMatricesForEmissionSeqOfCandidateMOH[2] = emissionSeqOfCandidateMOH;
		trainedMatrices.put(mohId, trainedMatricesForEmissionSeqOfCandidateMOH);
		neighbors.add(mohId);
		
		
		//get the rain fall sequence of candidate MOH  to train (consider rainfall as another neighbor with MOH id = 400)
		String emissionRainFallSeq = getEmissionRainFallSeq(mohName, "mohWeather.csv", noOfWeeksToTrain, rainFallDiscretizar);
		
		//get the observed rain fall sequence of candidate MOH to test
		ArrayList<Integer> emissionRainFallSeqOfCandidateMOHtoTest = getEmissionRainFallSeqOfCandidateMOHtoTest(mohName, "mohWeather.csv", noOfWeeksToTrain, rainFallDiscretizar);
		dengueMobilitySequencesOfNeighborsToTest.put(400, emissionRainFallSeqOfCandidateMOHtoTest);
		
		//get trained matrices for emissionSeqOfCandidateMOH
		String[] tranAndEmisMatricesForEmissionRainFallSeqOfCandidateMOH = trainHMM(proxy, processor, hiddenStateSeq, emissionRainFallSeq);
				
		String[] trainedMatricesForEmissionRainFallSeqOfCandidateMOH = new String[3];
				
		trainedMatricesForEmissionRainFallSeqOfCandidateMOH[0] = tranAndEmisMatricesForEmissionRainFallSeqOfCandidateMOH[0];
		trainedMatricesForEmissionRainFallSeqOfCandidateMOH[1] = tranAndEmisMatricesForEmissionRainFallSeqOfCandidateMOH[1];
		trainedMatricesForEmissionRainFallSeqOfCandidateMOH[2] = emissionRainFallSeq;
		trainedMatrices.put(400, trainedMatricesForEmissionRainFallSeqOfCandidateMOH);
		neighbors.add(400);
		
		
		
		
		//cannot test for the last week - reason for '-1'
		int noOfTests = dengueMobilitySequencesOfNeighborsToTest.get(neighbors.get(0)).size() - 1;
		
		// for each test case
		for (int k = 1; k < noOfTests; k++) {
			float sumOfWeights = 0;
			float normalizedSumOfPredictions = 0;

			// for each neighbor
			for (int id : neighbors) {	
				 
				String[] trained_matrices = trainedMatrices.get(id);
				ArrayList<Integer> dengueMobilitySequence = dengueMobilitySequencesOfNeighborsToTest.get(id);
				String thisWeek = Integer.toString(dengueMobilitySequence.get(k));
				String lastWeek = Integer.toString(dengueMobilitySequence.get(k - 1));

				String TRAN_MAT = trained_matrices[0];
				String EMIS_MAT = trained_matrices[1];
				String emissionSequence = trained_matrices[2];

				int prediction = getPrediction(proxy, processor, TRAN_MAT, EMIS_MAT, emissionSequence, thisWeek);
				int prevPrediction = getPrediction(proxy, processor, TRAN_MAT, EMIS_MAT, emissionSequence, lastWeek);

				// actual hidden state of previous week
				int actualValue = hiddenStateTestSeq.get(k - 1);
				int error = prevPrediction - actualValue;
				float weight = 1;
				if (error == 0) {
					weight = 1;
				} else {
					weight = (float) 1 / Math.abs(error);
				}
				normalizedSumOfPredictions += weight * prediction;
				sumOfWeights += weight;
			}

			int finalPrediction = (int) (normalizedSumOfPredictions / sumOfWeights);
			// System.out.println(finalPrediction + " " + hiddenStateTestSeq.get(k));
			System.out.print("plotPoint(" + Integer.toString(k - 1) + ", " + finalPrediction + ");");
		}

		System.out.print("series(\"Actual\", \"m+\");");
		// i = 1 since we cannot predict the first week
		for (int i = 1; i < noOfTests; i++) {
			System.out.print("plotPoint(" + Integer.toString(i - 1) + ", " + hiddenStateTestSeq.get(i) + ");");
		}
		proxy.disconnect();
	}

	
	
	
	
	
	/**
	 * getting next weeks prediction using the trained HMM
	 * @param proxy
	 * @param processor
	 * @param TRANS_EST
	 * @param EMIS_EST
	 * @param emissionSeq
	 * @param testSeq
	 * @return predicted dengue level of candidate MOH
	 * @throws MatlabInvocationException
	 */
	public static int getPrediction(MatlabProxy proxy, MatlabTypeConverter processor, String TRANS_EST, String EMIS_EST,
			String emissionSeq, String testSeq) throws MatlabInvocationException {

		proxy.eval("MAT_T = [" + TRANS_EST + "]");
		proxy.eval("MAT_E = [" + EMIS_EST + "]");
		proxy.eval("emissionSeq = [" + emissionSeq + "]");
		proxy.eval("testSeq = [" + testSeq + "]");
		proxy.eval("M = max(emissionSeq)");
		proxy.eval("if (testSeq(1) > M) \n testSeq = [M] \n end");

		proxy.eval("result = hmmviterbi(testSeq, MAT_T, MAT_E)");

		return (int) processor.getNumericArray("result").getRealValue(0);
	}

	
	/**
	 * generate transition and emission matrices using the observed and hidden sequences
	 * @param proxy
	 * @param processor
	 * @param states
	 * @param seq
	 * @return transition and emission matrices
	 * @throws MatlabInvocationException
	 */
	public static String[] trainHMM(MatlabProxy proxy, MatlabTypeConverter processor, String states, String seq)
			throws MatlabInvocationException {
		proxy.eval("states = [" + states + "]");
		proxy.eval("seq = [" + seq + "]");
		proxy.eval("[TRANS_EST, EMIS_EST] = hmmestimate(seq, states)");

		double[][] TRANS_EST = processor.getNumericArray("TRANS_EST").getRealArray2D();
		double[][] EMIS_EST = processor.getNumericArray("EMIS_EST").getRealArray2D();

		String TRANS_EST_str = "";
		String EMIS_EST_str = "";

		for (int i = 0; i < TRANS_EST.length; i++) {
			for (int j = 0; j < TRANS_EST[i].length; j++) {
				TRANS_EST_str += Double.toString(TRANS_EST[i][j]);
				if (j != TRANS_EST[i].length - 1) {
					TRANS_EST_str += ", ";
				}
			}
			if (i != TRANS_EST.length - 1) {
				TRANS_EST_str += "; ";
			}
		}

		for (int i = 0; i < EMIS_EST.length; i++) {
			for (int j = 0; j < EMIS_EST[i].length; j++) {
				EMIS_EST_str += Double.toString(EMIS_EST[i][j]);
				if (j != EMIS_EST[i].length - 1) {
					EMIS_EST_str += ", ";
				}
			}
			if (i != EMIS_EST.length - 1) {
				EMIS_EST_str += "; ";
			}
		}

		String[] matrices = { TRANS_EST_str, EMIS_EST_str };
		return matrices;
	}
	
	
	/**
	 * get the id for a given moh name
	 * @param mohName
	 * @return id of a given moh area
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public static int getMohId(String mohName, String fileName) throws NumberFormatException, IOException {
		CsvReader mohAreas = null;
		int mohId = -1;
		try {
			mohAreas = new CsvReader(fileName);
			
			// read mohAreas.csv to get MOH Id of the considering MOH
			while (mohAreas.readRecord()) {
				if (mohAreas.get(1).equalsIgnoreCase(mohName)) {
					mohId = Integer.parseInt(mohAreas.get(0));
					break;
				}
			}			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			mohAreas.close();
		}
		return mohId;
	}
	
	
	/**
	 * 
	 * @param mohId
	 * @return arraylist that containg mobility between the candidate moh area and the other moh areas
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public static ArrayList<Double> getMobilitiesWithOthers(int mohId, String fileName) throws NumberFormatException, IOException {
		ArrayList<Double> mobilityWithOthers = new ArrayList<Double>();
		CsvReader mobility = null;
		try {
			mobility = new CsvReader(fileName);
			// read mobility.csv to get the mobility between considering MOH area and its neighboring MOH areas
			boolean isNotInMoh = false;
			boolean isMohFound = false;
			while (mobility.readRecord()) {
				if (Integer.parseInt(mobility.get(0)) == mohId) {
					isNotInMoh = false;
					isMohFound = true;
					mobilityWithOthers.add(Double.parseDouble(mobility.get(2)));
				} else {
					isNotInMoh = true;
				}
				if (isNotInMoh && isMohFound) {
					break;
				}
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			mobility.close();
		}
		return mobilityWithOthers;
	}
	
	
	/**
	 * 
	 * @param mobilityWithOthers
	 * @param fileName
	 * @return mobility per host with other MOH areas
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public static ArrayList<Double> getMobilityPerHostWithOthers(ArrayList<Double> mobilityWithOthers, String fileName) throws NumberFormatException, IOException {
		CsvReader population = null;
		ArrayList<Double> mobilityPerHostWithOthers = new ArrayList<Double>();
		try {
			population = new CsvReader(fileName);
			// get Normalized mobility values by dividing the mobility value by the population
			int i = 0;
			while (population.readRecord()) {
				mobilityPerHostWithOthers.add(mobilityWithOthers.get(i) / Integer.parseInt(population.get(2)));
				i++;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			population.close();
		}
		return mobilityPerHostWithOthers;
	}
	
	
	/**
	 * 
	 * @param mobilityPerHostWithOthers
	 * @param fileName
	 * @param noOfWeeksToTrain
	 * @return average mobility per infected host with others
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public static HashMap<Integer, Double> getAverageMobilityPerInfectedHostWithOthers(ArrayList<Double> mobilityPerHostWithOthers, String fileName, int noOfWeeksToTrain) throws NumberFormatException, IOException {
		CsvReader dengueCases = null;
		HashMap<Integer, Double> averageMobilityPerInfectedHostWithOthers = new HashMap<Integer, Double>();
		try {
			dengueCases = new CsvReader(fileName);
			//for each record
			while (dengueCases.readRecord()) {
				int recordMohId = Integer.parseInt(dengueCases.get(0));
				//adding number of total dengue cases of considering MOH
				int sumOfDengueCases = 0;
				for (int j = 3; j < noOfWeeksToTrain + 3; j++) {
					sumOfDengueCases += Integer.parseInt(dengueCases.get(j));
				}
				averageMobilityPerInfectedHostWithOthers.put(recordMohId, (mobilityPerHostWithOthers.get(recordMohId) * sumOfDengueCases) / noOfWeeksToTrain);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			dengueCases.close();
		}
		return averageMobilityPerInfectedHostWithOthers;
	}
	
	
	/**
	 * 
	 * @param mohId
	 * @param fileName
	 * @param noOfWeeksToTrain
	 * @param dengueLevelDiscretizar
	 * @return
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public static String getHiddenStateSeqOfCandidateMoh(int mohId, int startingWeekNo, String fileName, int noOfWeeksToTrain, HashMap<Integer, Integer> dengueLevelDiscretizar) throws NumberFormatException, IOException {
		CsvReader dengueCases = null;
		String hiddenStateSeq = "";
		try {
			dengueCases = new CsvReader(fileName);
			//for each record
			while (dengueCases.readRecord()) {
				int recordMohId = Integer.parseInt(dengueCases.get(0));
				if (recordMohId == mohId) {
					//startingWeekNo = 2 since the lag should be 1 week		
					for (int i = startingWeekNo + 1; i < noOfWeeksToTrain + 3; i++) {
						hiddenStateSeq += Integer.toString(dengueLevelDiscretizar.get(Integer.parseInt(dengueCases.get(i)))) + " ";
					}	
					break;
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			dengueCases.close();
		}
		return hiddenStateSeq;
	}
	
	
	/**
	 * 
	 * @param mohId
	 * @param fileName
	 * @param noOfWeeksToTrain
	 * @param dengueLevelDiscretizar
	 * @return
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public static ArrayList<Integer> getHiddenStateTestSeqOfCandidateMoh(int mohId, String fileName, int noOfWeeksToTrain, HashMap<Integer, Integer> dengueLevelDiscretizar) throws NumberFormatException, IOException {
		CsvReader dengueCases = null;
		ArrayList<Integer> hiddenStateTestSeq = new ArrayList<Integer>();
		try {
			dengueCases = new CsvReader(fileName);
			//for each record
			while (dengueCases.readRecord()) {
				int recordMohId = Integer.parseInt(dengueCases.get(0));
				if (recordMohId == mohId) {
					for (int i = noOfWeeksToTrain + 3; i < 54; i++) {
						hiddenStateTestSeq.add(dengueLevelDiscretizar.get(Integer.parseInt(dengueCases.get(i))));
					}	
					break;
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			dengueCases.close();
		}
		return hiddenStateTestSeq;
	}
	
	
	/**
	 * 
	 * @param averageMobilityPerInfectedHostWithOthers
	 * @param fileName
	 * @param mobilityThreshold
	 * @return
	 * @throws IOException
	 */
	public static ArrayList<Integer> getTheNeighborsList(HashMap<Integer, Double> averageMobilityPerInfectedHostWithOthers, String fileName, Double mobilityThreshold) throws IOException {
		CsvReader dengueCases = null;
		ArrayList<Integer> neighbors = new ArrayList<Integer>();
		try {
			dengueCases = new CsvReader(fileName);
			//for each record
			while (dengueCases.readRecord()) {
				int recordMohId = Integer.parseInt(dengueCases.get(0));
				double recordMohMobility = averageMobilityPerInfectedHostWithOthers.get(recordMohId);
				if (recordMohMobility > mobilityThreshold) {
					neighbors.add(recordMohId);
				}
			}
		} catch(FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			dengueCases.close();
		}
		return neighbors;
	}
	
	
	/**
	 * 
	 * @param neighbors
	 * @param mobilityPerHostWithOthers
	 * @param fileName
	 * @param noOfWeeksToTrain
	 * @param dengueMobilityDiscretizar
	 * @return
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public static HashMap<Integer, ArrayList<Integer>> getDengueMobilitySequencesOfNeighborsToTrain(ArrayList<Integer> neighbors, ArrayList<Double> mobilityPerHostWithOthers, String fileName, int noOfWeeksToTrain, HashMap<Integer, Integer> dengueMobilityDiscretizar) throws NumberFormatException, IOException {
		CsvReader dengueCases = null;
		HashMap<Integer, ArrayList<Integer>> dengueMobilitySequencesOfNeighborsToTrain = new HashMap<Integer, ArrayList<Integer>>();
		try {
			dengueCases = new CsvReader(fileName);
			//for each record
			while (dengueCases.readRecord()) {
				int recordMohId = Integer.parseInt(dengueCases.get(0));
				ArrayList<Integer> recordMohDengueMobility = new ArrayList<Integer>();
				if (neighbors.contains(recordMohId)) {
					double recordMohMobility = mobilityPerHostWithOthers.get(recordMohId);
					for (int i = 2; i < noOfWeeksToTrain + 2; i++) {
						Integer dengueMobilityIntValue = (int) (Integer.parseInt(dengueCases.get(i)) * recordMohMobility);
						recordMohDengueMobility.add(dengueMobilityDiscretizar.get(dengueMobilityIntValue));
					}
					dengueMobilitySequencesOfNeighborsToTrain.put(recordMohId, recordMohDengueMobility);
				}
			}
		} catch(FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			dengueCases.close();
		}
		return dengueMobilitySequencesOfNeighborsToTrain;
	}
	
	
	/**
	 * 
	 * @param neighbors
	 * @param mobilityPerHostWithOthers
	 * @param fileName
	 * @param noOfWeeksToTrain
	 * @param dengueMobilityDiscretizar
	 * @return
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public static HashMap<Integer, ArrayList<Integer>> getDengueMobilitySequencesOfNeighborsToTest(ArrayList<Integer> neighbors, ArrayList<Double> mobilityPerHostWithOthers, String fileName, int noOfWeeksToTrain, HashMap<Integer, Integer> dengueMobilityDiscretizar) throws NumberFormatException, IOException {
		CsvReader dengueCases = null;
		HashMap<Integer, ArrayList<Integer>> dengueMobilitySequencesOfNeighborsToTest = new HashMap<Integer, ArrayList<Integer>>();
		try {
			dengueCases = new CsvReader(fileName);
			//for each record
			while (dengueCases.readRecord()) {
				int recordMohId = Integer.parseInt(dengueCases.get(0));
				ArrayList<Integer> recordMohDengueMobility = new ArrayList<Integer>();
				if (neighbors.contains(recordMohId)) {
					double recordMohMobility = mobilityPerHostWithOthers.get(recordMohId);
					for (int i = noOfWeeksToTrain + 2; i < 54; i++) {
						Integer dengueMobilityIntValue = (int) (Integer.parseInt(dengueCases.get(i)) * recordMohMobility);
						recordMohDengueMobility.add(dengueMobilityDiscretizar.get(dengueMobilityIntValue));
					}
					dengueMobilitySequencesOfNeighborsToTest.put(recordMohId, recordMohDengueMobility);
				}
			}
		} catch(FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			dengueCases.close();
		}
		return dengueMobilitySequencesOfNeighborsToTest;
	}
	
	/**
	 * 
	 * @param neighbors
	 * @param dengueMobilitySequencesOfNeighborsToTrain
	 * @param proxy
	 * @param processor
	 * @param hiddenStateSeq
	 * @param hmm
	 * @return
	 * @throws MatlabInvocationException
	 */
	public static HashMap<Integer, String[]> getTrainedMatrices(ArrayList<Integer> neighbors, HashMap<Integer, ArrayList<Integer>> dengueMobilitySequencesOfNeighborsToTrain, MatlabProxy proxy, MatlabTypeConverter processor, String hiddenStateSeq) throws MatlabInvocationException {
		HashMap<Integer, String[]> trainedMatrices = new HashMap<Integer, String[]>();
		// for each neighbor
		for(int id : neighbors) {
			String emissionSeq = "";
			// consider the dengue mobility value of each week
			for (int val : dengueMobilitySequencesOfNeighborsToTrain.get(id)) {
				emissionSeq += Integer.toString(val) + " ";
			}
			//train the model
			String TrainedMatrices[] = trainHMM(proxy, processor, hiddenStateSeq, emissionSeq);
			//TrainedMatrices[0] = transition matrix, TrainedMatrices[1] = emission matrix
			String[] neighborSequences = { TrainedMatrices[0], TrainedMatrices[1], emissionSeq };
			trainedMatrices.put(id, neighborSequences);
		}
		return trainedMatrices;
	}
	
	
	/**
	 * 
	 * @param mohId
	 * @param fileName
	 * @param noOfWeeksToTrain
	 * @param dengueLevelDiscretizer
	 * @return
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public static ArrayList<Integer> getEmissionSeqOfCandidateMOHtoTest(int mohId, String fileName, int noOfWeeksToTrain, HashMap<Integer, Integer> dengueLevelDiscretizer) throws NumberFormatException, IOException{
		CsvReader dengueCases = null;
		ArrayList<Integer> emissionSeqOfCandidateMOHtoTest = new ArrayList<Integer>();
		try {
			dengueCases = new CsvReader(fileName);
			//for each record
			while (dengueCases.readRecord()) {		
				int recordMohId = Integer.parseInt(dengueCases.get(0));
				if (recordMohId == mohId) {
					for (int i = noOfWeeksToTrain + 2; i < 54; i++) {
						Integer dengueLevelIntValue = dengueLevelDiscretizer.get(Integer.parseInt(dengueCases.get(i)));
						emissionSeqOfCandidateMOHtoTest.add(dengueLevelIntValue);
					}
					break;
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			dengueCases.close();
		}
		return emissionSeqOfCandidateMOHtoTest;
	}
	
	/**
	 * 
	 * @param mohName
	 * @param fileName
	 * @param noOfWeeksToTrain
	 * @param rainFallDiscretizer
	 * @return
	 * @throws IOException 
	 * @throws NumberFormatException 
	 */
	public static String getEmissionRainFallSeq(String mohName, String fileName, int noOfWeeksToTrain, HashMap<Integer, Integer> rainFallDiscretizer) throws NumberFormatException, IOException {
		CsvReader rainFallReader = null;
		String emissionRainFallSeq = "";
		try {
			rainFallReader = new CsvReader(fileName);
			//for each record
			while (rainFallReader.readRecord()) {
				String recordMohName = rainFallReader.get(0);
				if (recordMohName.equalsIgnoreCase(mohName)) {
					for (int i = 1; i < noOfWeeksToTrain + 1; i++) {
						Integer rainFallIntValue = (int)Double.parseDouble(rainFallReader.get(i));
						emissionRainFallSeq += Integer.toString(rainFallDiscretizer.get(rainFallIntValue)) + " ";
					}
					break;
				}
			}
		} catch(FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			rainFallReader.close();
		}	
		return emissionRainFallSeq;
	}
	
	/**
	 * 
	 * @param mohName
	 * @param fileName
	 * @param noOfWeeksToTrain
	 * @param rainFallDiscretizer
	 * @return
	 * @throws NumberFormatException
	 * @throws IOException
	 */
	public static ArrayList<Integer> getEmissionRainFallSeqOfCandidateMOHtoTest(String mohName, String fileName, int noOfWeeksToTrain, HashMap<Integer, Integer> rainFallDiscretizer) throws NumberFormatException, IOException {
		CsvReader rainFallReader = null;
		ArrayList<Integer> emissionRainFallSeqOfCandidateMOHtoTest = new ArrayList<Integer>();
		try {
			rainFallReader = new CsvReader(fileName);
			//for each record
			while (rainFallReader.readRecord()) {		
				String recordMohName = rainFallReader.get(0);
				if (recordMohName.equalsIgnoreCase(mohName)) {
					for (int i = noOfWeeksToTrain + 1; i < 53; i++) {
						int rainFallIntValue = rainFallDiscretizer.get((int) Double.parseDouble(rainFallReader.get(i)));
						emissionRainFallSeqOfCandidateMOHtoTest.add(rainFallIntValue);
					}
					break;
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			rainFallReader.close();
		}
		return emissionRainFallSeqOfCandidateMOHtoTest;
	}
}
