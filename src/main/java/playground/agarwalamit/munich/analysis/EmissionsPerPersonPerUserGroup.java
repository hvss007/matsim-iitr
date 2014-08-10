/* *********************************************************************** *
 * project: org.matsim.*
 * EmissionsPerPersonAnalysis.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package playground.agarwalamit.munich.analysis;

import java.io.BufferedWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.emissions.types.WarmPollutant;
import org.matsim.contrib.emissions.utils.EmissionUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.MatsimConfigReader;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;

import playground.agarwalamit.analysis.emission.EmissionCostFactors;
import playground.benjamin.scenarios.munich.analysis.filter.PersonFilter;
import playground.benjamin.scenarios.munich.analysis.filter.UserGroup;
import playground.vsp.analysis.modules.emissionsAnalyzer.EmissionsAnalyzer;

/**
 * @author amit
 *
 */
public class EmissionsPerPersonPerUserGroup {

private final Logger logger = Logger.getLogger(EmissionsPerPersonPerUserGroup.class);
	
public EmissionsPerPersonPerUserGroup() {
		scenario = loadScenario(networkFile, populationFile);
		
		for(UserGroup ug:UserGroup.values()){
			SortedMap<String, Double> pollutantToValue = new TreeMap<String, Double>();
			for(WarmPollutant wm:WarmPollutant.values()){ //because ('warmPollutants' U 'coldPollutants') = 'warmPollutants'
				pollutantToValue.put(wm.toString(), 0.0);
			}
			userGroupToEmissions.put(ug, pollutantToValue);
		}
		lastIteration = getLastIteration(configFile);
	}
	
	private  int lastIteration;
	private  String outputDir = "/Users/aagarwal/Desktop/ils4/agarwal/munich/output/1pct/ci/";/*"./output/run2/";*/
	private  String populationFile =outputDir+ "/output_plans.xml.gz";//"/network.xml";
	private  String networkFile =outputDir+ "/output_network.xml.gz";//"/network.xml";
	private  String configFile = outputDir+"/output_config.xml";
	private Map<UserGroup, SortedMap<String, Double>> userGroupToEmissions = new HashMap<UserGroup, SortedMap<String,Double>>();
	private Map<UserGroup, Population> userGrpToPopulation = new HashMap<UserGroup, Population>();
	private Scenario scenario;
	private Map<Id, SortedMap<String, Double>> emissionsPerPerson = new HashMap<Id, SortedMap<String,Double>>();
	
	public static void main(String[] args) {
		EmissionsPerPersonPerUserGroup eppa = new EmissionsPerPersonPerUserGroup();
		eppa.run();
	}

	private void run() {
		String emissionEventFile = outputDir+"/ITERS/it."+lastIteration+"/"+lastIteration+".emission.events.xml.gz";//"/events.xml";//
		EmissionsAnalyzer ema = new EmissionsAnalyzer(emissionEventFile);
		ema.init((ScenarioImpl) scenario);
		ema.preProcessData();
		ema.postProcessData();

		EmissionUtils emu = new EmissionUtils();
		Map<Id, SortedMap<String, Double>> totalEmissions = ema.getPerson2totalEmissions();
		emissionsPerPerson = emu.setNonCalculatedEmissionsForPopulation(scenario.getPopulation(), totalEmissions);

		getPopulationPerUserGroup();
		getTotalEmissionsPerUserGroup(emissionsPerPerson);
		writeTotalEmissionsPerUserGroup(outputDir+"/analysis/userGrpEmissions.txt");
		writeTotalEmissionsCostsPerUserGroup(outputDir+"/analysis/userGrpEmissionsCosts.txt");
	}

	private void writeTotalEmissionsCostsPerUserGroup(String outputFile){
		BufferedWriter writer = IOUtils.getBufferedWriter(outputFile);
		try{
			writer.write("userGroup \t emissionType \t emissionsCosts(mU)  \n");
			for(UserGroup ug:this.userGroupToEmissions.keySet()){
				double totalEmissionCost =0. ;
				for(EmissionCostFactors ecf:EmissionCostFactors.values()){
					double ec = this.userGroupToEmissions.get(ug).get(ecf.toString()) * ecf.getCostFactor();
					writer.write(ug+"\t"+ecf+"\t"+ec+"\n");
					totalEmissionCost += ec;
				}
				writer.write(ug+"\t"+"total \t"+totalEmissionCost+"\n");
			}
			writer.close();
		} catch (Exception e){
			throw new RuntimeException("Data is not written in the file. Reason - "+e);
		}
		logger.info("Finished Writing files to file "+outputFile);		
	}
	
	private void writeTotalEmissionsPerUserGroup(String outputFile) {

		BufferedWriter writer = IOUtils.getBufferedWriter(outputFile);
		try{
			writer.write("userGroup \t pollutantType \t pollutantValue  \n");
			for(UserGroup ug:this.userGroupToEmissions.keySet()){
				for(String str:this.userGroupToEmissions.get(ug).keySet()){
					writer.write(ug+"\t"+str+"\t"+this.userGroupToEmissions.get(ug).get(str)+"\n");
				}
			}
			writer.close();
		} catch (Exception e){
			throw new RuntimeException("Data is not written in the file. Reason - "+e);
		}
		logger.info("Finished Writing files to file "+outputFile);		
	}

	private void getTotalEmissionsPerUserGroup(
			Map<Id, SortedMap<String, Double>> emissionsPerPerson) {
		for(Id personId: scenario.getPopulation().getPersons().keySet()){
			UserGroup ug = getUserGroupFromPersonId(personId);
			SortedMap<String, Double> emissionsNewValue = new TreeMap<String, Double>();
			for(String str: emissionsPerPerson.get(personId).keySet()){
				double emissionSoFar = userGroupToEmissions.get(ug).get(str);
				double emissionNewValue = emissionSoFar+emissionsPerPerson.get(personId).get(str);
				emissionsNewValue.put(str, emissionNewValue);
			}
			userGroupToEmissions.put(ug, emissionsNewValue);
		}
	}

	private static Scenario loadScenario(String netFile, String plansFile) {
		Config config = ConfigUtils.createConfig();
		config.network().setInputFile(netFile);
		config.plans().setInputFile(plansFile);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		return scenario;
	}

	private static Integer getLastIteration(String configFile) {
		Config config = new Config();
		config.addCoreModules();
		MatsimConfigReader configReader = new MatsimConfigReader(config);
		configReader.readFile(configFile);
		Integer lastIt = config.controler().getLastIteration();
		return lastIt;
	}

	private Map<UserGroup, Population> getPopulationPerUserGroup(){
		PersonFilter pf = new PersonFilter();
		for(UserGroup ug : UserGroup.values()){
			userGrpToPopulation.put(ug, pf.getPopulation(scenario.getPopulation(), ug));
		}
		return userGrpToPopulation;
	}

	private UserGroup getUserGroupFromPersonId(Id personId){
		UserGroup usrgrp = null;
		for(UserGroup ug:userGrpToPopulation.keySet()){
			if(userGrpToPopulation.get(ug).getPersons().get(personId)!=null) {
				usrgrp = ug;
				break;
			}
		}
		return usrgrp;
	}
}