package com.vidura.fyp.hmm;

import matlabcontrol.*;
import matlabcontrol.extensions.MatlabTypeConverter;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.csvreader.CsvReader;

public class HMModel {

	public static void main(String[] args) throws MatlabConnectionException, MatlabInvocationException {

		String mohName = "Kaduwela";
		int noOfWeeksToTrain = 40; // maximum number of training weeks is 50 since we can use only 51 weeks and at least one week is needed for testing
		int sizeOfDengueInterval = 10;
		int sizeOfMobilityInterval = 20;
		int maxNoOfDengueCases = 200;
		int maxMobilityValue = 20000;
		int maximumDengueMobilityValueOfTheTrainingSequence = 200;
		Double mobilityThreshold = 1.0;

		String hiddenStateSeq = "";
		int mohId = -1;
		CsvReader mohAreas = null;
		CsvReader mobility = null;
		CsvReader population = null;
		CsvReader dengueCases = null;
		List<Integer> hiddenStateTestSeq = new ArrayList<Integer>();
		List<Double> mobilityWithNeighbors = new ArrayList<Double>();
		List<Double> normalizedMobilityWithNeighbors = new ArrayList<Double>();
		List<ArrayList<Integer>> dengueMobility = new ArrayList<ArrayList<Integer>>();
		List<ArrayList<Integer>> testDengueMobility = new ArrayList<ArrayList<Integer>>();
		HashMap<Integer, Integer> dengueLevelDiscretizar = new HashMap<Integer, Integer>();
		HashMap<Integer, Integer> dengueMobilityDiscretizar = new HashMap<Integer, Integer>();
		HashMap<Integer, Double> finalNormalizedMobilityWithNeighbors = new HashMap<Integer, Double>();

		MatlabProxyFactory factory = new MatlabProxyFactory();
		MatlabProxy proxy = factory.getProxy();
		MatlabTypeConverter processor = new MatlabTypeConverter(proxy);
		HMModel hmm = new HMModel();

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
				//removing unknown maximum states
				if (i > maximumDengueMobilityValueOfTheTrainingSequence)
					j--;
				j++;
			}
			dengueMobilityDiscretizar.put(i, j);
		}

		try {
			mohAreas = new CsvReader("mohAreas.csv");
			mobility = new CsvReader("mobility.csv");
			population = new CsvReader("resident_work_processed.csv");
			dengueCases = new CsvReader("dengueCases.csv");

			// read mohAreas.csv to get MOH Id of the considering MOH
			while (mohAreas.readRecord()) {
				if (mohAreas.get(1).equalsIgnoreCase(mohName)) {
					mohId = Integer.parseInt(mohAreas.get(0));
					break;
				}
			}
			if (mohId < 0) {
				System.out.println("MOH not found");
				return;
			}

			// read mobility.csv to get the mobility between considering MOH
			// area and its neighboring MOH areas
			boolean isNotInMoh = false;
			boolean isMohFound = false;
			while (mobility.readRecord()) {
				if (Integer.parseInt(mobility.get(0)) == mohId) {
					isNotInMoh = false;
					isMohFound = true;
					mobilityWithNeighbors.add(Double.parseDouble(mobility.get(2)));
				} else {
					isNotInMoh = true;
				}
				if (isNotInMoh && isMohFound) {
					break;
				}
			}

			// get Normalized mobility values by dividing the mobility value by
			// the population
			int i = 0;
			while (population.readRecord()) {
				normalizedMobilityWithNeighbors.add(mobilityWithNeighbors.get(i) / Integer.parseInt(population.get(2)));
				i++;
			}			
			// Get dengue cases to calculate dengue mobility sequences
			while (dengueCases.readRecord()) {
				int recordMohId = Integer.parseInt(dengueCases.get(0));
				
				
				
				
				// TODO adding number of total dengue cases to calculate dengue mobility
				int sumOfDengueCases = 0;
				for (int j = 3; j < noOfWeeksToTrain + 3; j++) {
					sumOfDengueCases += Integer.parseInt(dengueCases.get(j));
				}
				finalNormalizedMobilityWithNeighbors.put(recordMohId, (normalizedMobilityWithNeighbors.get(recordMohId) * sumOfDengueCases)/noOfWeeksToTrain);
				
				
				
				
				if (recordMohId == mohId) {
					// j = 3 since the lag should be 1 week
					for (int j = 3; j < noOfWeeksToTrain + 3; j++) {
						hiddenStateSeq += Integer
								.toString(dengueLevelDiscretizar.get(Integer.parseInt(dengueCases.get(j)))) + " ";
					}
					for (int j1 = noOfWeeksToTrain + 3; j1 < 54; j1++) {
						hiddenStateTestSeq.add(dengueLevelDiscretizar.get(Integer.parseInt(dengueCases.get(j1))));
					}
					continue;
				}

				double recordMohMobility = finalNormalizedMobilityWithNeighbors.get(recordMohId);
				//System.out.println("************************    " + recordMohMobility + "        " + sumOfDengueCases);
				if (recordMohMobility > mobilityThreshold) {
					
					//System.out.println("************************    " + recordMohMobility + "        " + sumOfDengueCases);
					ArrayList<Integer> recordMohDengueMobility = new ArrayList<Integer>();
					ArrayList<Integer> recordTestMohDengueMobility = new ArrayList<Integer>();
					for (int j = 2; j < noOfWeeksToTrain + 2; j++) {
						Integer dengueMobilityIntValue = (int) (Integer.parseInt(dengueCases.get(j))
								* recordMohMobility);
						//System.out.println("*****************   " + dengueMobilityIntValue + "     " + Integer.parseInt(dengueCases.get(j))	* recordMohMobility);
						recordMohDengueMobility.add(dengueMobilityDiscretizar.get(dengueMobilityIntValue));
						//System.out.println("*****************    " + dengueMobilityDiscretizar.get(dengueMobilityIntValue) + "      " + dengueMobilityIntValue);
					}
					for (int j1 = noOfWeeksToTrain + 2; j1 < 54; j1++) {
						Integer testDengueMobilityIntValue = (int) (Integer.parseInt(dengueCases.get(j1))
								* recordMohMobility);
						recordTestMohDengueMobility.add(dengueMobilityDiscretizar.get(testDengueMobilityIntValue));
					}
					dengueMobility.add(recordMohDengueMobility);
					testDengueMobility.add(recordTestMohDengueMobility);
				}
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			mohAreas.close();
			mobility.close();
			population.close();
			dengueCases.close();
		}

		int noOfTests = 51 - noOfWeeksToTrain;

		System.out.println("figure(\"" + mohName + "\"); clear();");
		System.out.println("series(\"Predicted\", \"ko:\");");

		for (int k = 1; k < noOfTests; k++) {
			int counter = 0;
			float sumOfWeights = 0;
			float normalizedSumOfPredictions = 0;
			//for each neighbor, 
			for (ArrayList<Integer> array : dengueMobility) {
				String emissionSeq = "";
				//consider the dengue mobility value of each week
				for (int val : array) {
					emissionSeq += Integer.toString(val) + " ";
				}
				ArrayList<Integer> arrayTest = testDengueMobility.get(counter);
				String thisWeek = Integer.toString(arrayTest.get(k));
				String lastWeek = Integer.toString(arrayTest.get(k - 1));

				int prediction = hmm.getPrediction(proxy, processor, hiddenStateSeq, emissionSeq, thisWeek);
				int prevPrediction = hmm.getPrediction(proxy, processor, hiddenStateSeq, emissionSeq, lastWeek);

				// used k - 1 instead of k since hidden states start from week 2
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

				counter++;
			}

			int finalPrediction = (int) (normalizedSumOfPredictions / sumOfWeights);
			//System.out.println(finalPrediction + "    " + hiddenStateTestSeq.get(k));
			System.out.print("plotPoint(" + Integer.toString(k - 1) + ", " + finalPrediction + ");");
		}
		
		
		System.out.println("\nseries(\"Actual\", \"m+\");");
		//i1 = 1 since we cannot predict the first week
		for (int i1 = 1; i1 < noOfTests; i1++) {
			System.out.print("plotPoint(" + Integer.toString(i1 - 1) + ", " + hiddenStateTestSeq.get(i1) + ");");
		}
		proxy.disconnect();
	}

	public int getPrediction(MatlabProxy proxy, MatlabTypeConverter processor, String states, String seq,
			String testSeq) throws MatlabInvocationException {
		proxy.eval("states = [" + states + "]");
		proxy.eval("seq = [" + seq + "]");
		proxy.eval("[TRANS_EST, EMIS_EST] = hmmestimate(seq, states)");

		proxy.eval("testSeq = [" + testSeq + "]");
		proxy.eval("result = hmmviterbi(testSeq, TRANS_EST, EMIS_EST)");

		return (int) processor.getNumericArray("result").getRealValue(0);
	}

}
