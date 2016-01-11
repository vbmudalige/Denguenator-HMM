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
		int noOfWeeksToTrain = 35; // maximum number of training weeks is 51
		int sizeOfDengueInterval = 5;
		int sizeOfMobilityInterval = 2;
		int maxNoOfDengueCases = 200;
		int maxMobilityValue = 200;
		Double mobilityThreshold = 0.1;

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
				double recordMohMobility = normalizedMobilityWithNeighbors.get(recordMohId);
				if (recordMohMobility > mobilityThreshold) {
					ArrayList<Integer> recordMohDengueMobility = new ArrayList<Integer>();
					ArrayList<Integer> recordTestMohDengueMobility = new ArrayList<Integer>();
					for (int j = 2; j < noOfWeeksToTrain + 2; j++) {
						Integer dengueMobilityIntValue = (int) (Integer.parseInt(dengueCases.get(j))
								* recordMohMobility);
						recordMohDengueMobility.add(dengueMobilityDiscretizar.get(dengueMobilityIntValue));
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

		int noOfTests = 49 - noOfWeeksToTrain;

		for (int k = 1; k < noOfTests; k++) {
			int counter = 0;
			float sumOfWeights = 0;
			float normalizedSumOfPredictions = 0;
			for (ArrayList<Integer> array : dengueMobility) {
				String emissionSeq = "";
				for (int val : array) {
					emissionSeq += Integer.toString(val) + " ";
				}
				ArrayList<Integer> arrayTest = testDengueMobility.get(counter);
				String thisWeek = Integer.toString(arrayTest.get(k));
				String lastWeek = Integer.toString(arrayTest.get(k - 1));

				int prediction = hmm.getPrediction(proxy, processor, hiddenStateSeq, emissionSeq, thisWeek);
				int prevPrediction = hmm.getPrediction(proxy, processor, hiddenStateSeq, emissionSeq, lastWeek);

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
			System.out.println(finalPrediction + "    " + hiddenStateTestSeq.get(k));
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
