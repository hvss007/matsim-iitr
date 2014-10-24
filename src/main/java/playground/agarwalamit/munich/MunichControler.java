/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
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
package playground.agarwalamit.munich;

import org.matsim.contrib.emissions.EmissionModule;
import org.matsim.contrib.emissions.example.EmissionControlerListener;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.roadpricing.TravelDisutilityIncludingToll;
import org.matsim.vis.otfvis.OTFFileWriterFactory;

import playground.agarwalamit.InternalizationEmissionAndCongestion.EmissionCongestionTravelDisutilityCalculatorFactory;
import playground.agarwalamit.InternalizationEmissionAndCongestion.InternalizeEmissionsCongestionControlerListener;
import playground.benjamin.internalization.EmissionCostModule;
import playground.benjamin.internalization.EmissionTravelDisutilityCalculatorFactory;
import playground.benjamin.internalization.InternalizeEmissionsControlerListener;
import playground.ikaddoura.internalizationCar.MarginalCostPricing;
import playground.ikaddoura.internalizationCar.TollDisutilityCalculatorFactory;
import playground.ikaddoura.internalizationCar.TollHandler;
import playground.ikaddoura.internalizationCar.WelfareAnalysisControlerListener;

/**
 * @author amit
 */
public class MunichControler {

	public static void main(String[] args) {
		
//		String clusterFolder = "/Users/aagarwal/Desktop/ils4/agarwal/munich/";
//		args = new String [5];
//		args[0] =String.valueOf("false");
//		args[1] ="true";
//		args[2] ="false";
//		args[3] =clusterFolder+"input/config_munich_1pct_baseCase.xml";
//		args[4] ="./output/baseCase/";
		
		boolean internalizeEmission = Boolean.valueOf(args [0]); 
		boolean internalizeCongestion = Boolean.valueOf(args [1]);
		boolean both = Boolean.valueOf(args [2]);
		String configFile = args[3];

		String emissionEfficiencyFactor ="1.0";
		String considerCO2Costs = "true";
		String emissionCostFactor = "1.0";

		Config config = ConfigUtils.loadConfig(configFile);
		config.controler().setOutputDirectory(args[4]);
		
//		config.network().setInputFile(clusterFolder+"input/network-86-85-87-84_simplifiedWithStrongLinkMerge---withLanes.xml");
//		config.plans().setInputFile(clusterFolder+"input/mergedPopulation_All_1pct_scaledAndMode_workStartingTimePeakAllCommuter0800Var2h_gk4.xml.gz");
//		config.counts().setCountsFileName(clusterFolder+"input/counts-2008-01-10_correctedSums_manuallyChanged_strongLinkMerge.xml");

		//===vsp defaults
//		config.vspExperimental().setRemovingUnneccessaryPlanAttributes(true);
//		config.vspExperimental().setActivityDurationInterpretation(ActivityDurationInterpretation.tryEndTimeThenDuration.toString());
//		config.timeAllocationMutator().setMutationRange(7200.);
//		config.timeAllocationMutator().setAffectingDuration(false);
//		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.ABORT);

		Controler controler = new Controler(config);

		//===emission files
		
	      EmissionsConfigGroup ecg = new EmissionsConfigGroup();
	      controler.getConfig().addModule(ecg);
	    
		ecg.setAverageColdEmissionFactorsFile("../../matsimHBEFAStandardsFiles/EFA_ColdStart_vehcat_2005average.txt");
		ecg.setAverageWarmEmissionFactorsFile("../../matsimHBEFAStandardsFiles/EFA_HOT_vehcat_2005average.txt");
		ecg.setDetailedColdEmissionFactorsFile("../../matsimHBEFAStandardsFiles/EFA_ColdStart_SubSegm_2005detailed.txt");
		ecg.setDetailedWarmEmissionFactorsFile("../../matsimHBEFAStandardsFiles/EFA_HOT_SubSegm_2005detailed.txt");
		ecg.setEmissionRoadTypeMappingFile("../../munich/input/roadTypeMapping.txt");
		ecg.setEmissionVehicleFile("../../munich/input/emissionVehicles_1pct.xml.gz");
	      
//	      	ecg.setAverageColdEmissionFactorsFile("/Users/aagarwal/Desktop/ils4/agarwal/matsimHBEFAStandardsFiles/EFA_ColdStart_vehcat_2005average.txt");
//			ecg.setAverageWarmEmissionFactorsFile("/Users/aagarwal/Desktop/ils4/agarwal/matsimHBEFAStandardsFiles/EFA_HOT_vehcat_2005average.txt");
//			ecg.setDetailedColdEmissionFactorsFile("/Users/aagarwal/Desktop/ils4/agarwal/matsimHBEFAStandardsFiles/EFA_ColdStart_SubSegm_2005detailed.txt");
//			ecg.setDetailedWarmEmissionFactorsFile("/Users/aagarwal/Desktop/ils4/agarwal/matsimHBEFAStandardsFiles/EFA_HOT_SubSegm_2005detailed.txt");
//			ecg.setEmissionRoadTypeMappingFile("/Users/aagarwal/Desktop/ils4/agarwal/munich/input/roadTypeMapping.txt");
//			ecg.setEmissionVehicleFile("/Users/aagarwal/Desktop/ils4/agarwal/munich/input/emissionVehicles_1pct.xml.gz");
	      
		ecg.setUsingDetailedEmissionCalculation(true);
		//===only emission events genertaion; used with all runs for comparisons
		EmissionModule emissionModule = new EmissionModule(ScenarioUtils.loadScenario(config));
		emissionModule.setEmissionEfficiencyFactor(Double.parseDouble(emissionEfficiencyFactor));
		emissionModule.createLookupTables();
		emissionModule.createEmissionHandler();
		
		if(internalizeEmission)
		{
			//===internalization of emissions
			
			// this is needed by *both* following modules:
			EmissionCostModule emissionCostModule = new EmissionCostModule(Double.parseDouble(emissionCostFactor), Boolean.parseBoolean(considerCO2Costs));

			// this affects the router by overwriting its generalized cost function (TravelDisutility):
			EmissionTravelDisutilityCalculatorFactory emissionTducf = new EmissionTravelDisutilityCalculatorFactory(emissionModule, emissionCostModule);
			controler.setTravelDisutilityFactory(emissionTducf);
			
			// this is essentially the syntax to use the randomizing router instead; needs "scheme" (which implements RoadPricingScheme); needs
			// a way to insert a new scheme in every iteration (because emissions costs change by iteration).
//			TravelDisutilityIncludingToll.Builder travelDisutilityFactory = new TravelDisutilityIncludingToll.Builder(
//					controler.getTravelDisutilityFactory(), scheme, controler.getConfig().planCalcScore().getMarginalUtilityOfMoney()
//					) ;
//			travelDisutilityFactory.setSigma( 3. );
//			controler.setTravelDisutilityFactory(travelDisutilityFactory);

			// this generates money events and thus affects the scoring:
			controler.addControlerListener(new InternalizeEmissionsControlerListener(emissionModule, emissionCostModule));
		}

		if(internalizeCongestion) 
		{
			//=== internalization of congestion
			TollHandler tollHandler = new TollHandler(controler.getScenario());
			TollDisutilityCalculatorFactory tollDisutilityCalculatorFactory = new TollDisutilityCalculatorFactory(tollHandler);
			controler.setTravelDisutilityFactory(tollDisutilityCalculatorFactory);
			controler.addControlerListener(new MarginalCostPricing((ScenarioImpl) controler.getScenario(), tollHandler ));
		}
		
		if(both) {
			TollHandler tollHandler = new TollHandler(controler.getScenario());
			EmissionCostModule emissionCostModule = new EmissionCostModule(Double.parseDouble(emissionCostFactor), Boolean.parseBoolean(considerCO2Costs));
			EmissionCongestionTravelDisutilityCalculatorFactory emissionCongestionTravelDisutilityCalculatorFactory = new EmissionCongestionTravelDisutilityCalculatorFactory(emissionModule, emissionCostModule, tollHandler);
			controler.setTravelDisutilityFactory(emissionCongestionTravelDisutilityCalculatorFactory);
			controler.addControlerListener(new InternalizeEmissionsCongestionControlerListener(emissionModule, emissionCostModule, (ScenarioImpl) controler.getScenario(), tollHandler));
		}

		controler.setOverwriteFiles(true);
		controler.setCreateGraphs(true);
		controler.setDumpDataAtEnd(true);
		controler.addSnapshotWriterFactory("otfvis", new OTFFileWriterFactory());
		controler.addControlerListener(new WelfareAnalysisControlerListener((ScenarioImpl) controler.getScenario()));
		
		if(internalizeEmission==false && both==false){
			controler.addControlerListener(new EmissionControlerListener());
		}
		controler.run();	

	}

}
