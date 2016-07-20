/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
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

package playground.agarwalamit.mixedTraffic.patnaIndia.input.combined;

import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.controler.listener.StartupListener;

import com.google.inject.Inject;

import playground.agarwalamit.analysis.modalShare.ModalShareEventHandler;
import playground.agarwalamit.utils.MapUtils;

/**
 * @author amit
 */

public class ASCFromModalSplitCalibrator implements StartupListener, IterationStartsListener, IterationEndsListener {

	private static final Logger LOG = Logger.getLogger(ASCFromModalSplitCalibrator.class);
	
	private SortedMap<String, Double> initialMode2share ; // if empty, take from it.0
	private SortedMap<String, Double> previousASC ; // all zeros
	
	// following is scenario specific (using for Patna)
	private final List<String> externalModes = Arrays.asList("car_ext","bike_ext","motorbike_ext","truck_ext");

	@Inject ModalShareEventHandler modalShareEventHandler;
	@Inject Scenario scenario;

	private int updateASCAfterIts = 10;
	private int innovationStop ;

	public ASCFromModalSplitCalibrator(final SortedMap<String, Double> mode2share, final int updateASCIterations){
		this.initialMode2share = mode2share;
		this.updateASCAfterIts = updateASCIterations;
		initializeInitialASC();
	}

	public ASCFromModalSplitCalibrator(final int updateASCIterations){
		this.initialMode2share = new TreeMap<>();
		this.updateASCAfterIts = updateASCIterations;
	}
	
	@Override
	public void notifyStartup(StartupEvent event) {
		Config config =event.getServices().getScenario().getConfig();
		this.innovationStop = (int) ( ( config.controler().getLastIteration() - config.controler().getFirstIteration() ) 
				/ config.strategy().getFractionOfIterationsToDisableInnovation() ); 
	}
	
	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		int itr = event.getIteration();
		
		if( this.initialMode2share.isEmpty() && itr==0 ) { // store initial modal split if not provided
			
			SortedMap<String, Integer> mode2legs = modalShareEventHandler.getMode2numberOflegs();
			
			// following is Patna specific only, I dont want to get share for external modes.
			mode2legs.keySet().removeAll(externalModes);
			this.initialMode2share = MapUtils.getIntPercentShare(mode2legs);
			
			initializeInitialASC();
			
			event.getServices().getEvents().removeHandler(modalShareEventHandler);
			
		} else if( itr%updateASCAfterIts==0 && itr <= innovationStop ) { //AA_TODO: not sure if update it during innovation stop 
			
			SortedMap<String, Integer> mode2legs = modalShareEventHandler.getMode2numberOflegs();
			mode2legs.keySet().removeAll(externalModes);
			SortedMap<String, Double> mode2shre = MapUtils.getIntPercentShare(mode2legs);

			// update ascs
			updateASC(mode2shre);
			
			// update in scenario
			for (String mode : this.previousASC.keySet()) {
				event.getServices().getScenario().getConfig().planCalcScore().getOrCreateModeParams(mode).setConstant(this.previousASC.get(mode));
			}
			
			event.getServices().getEvents().removeHandler(modalShareEventHandler);
		}
		event.getServices().getEvents().removeHandler(modalShareEventHandler);
	}

	@Override
	public void notifyIterationStarts(IterationStartsEvent event) {
		int itr = event.getIteration();
		if( this.initialMode2share.isEmpty() && itr==0 ) { // store initial modal split it not provided
			event.getServices().getEvents().addHandler(modalShareEventHandler);
		} else if(itr%updateASCAfterIts==0 && itr <= innovationStop ) {
			event.getServices().getEvents().addHandler(modalShareEventHandler);
		}
	}
	
	private void initializeInitialASC (){
		for (String mode : this.initialMode2share.keySet()){
			this.previousASC.put(mode, 0.0);
		}
	}
	
	private void updateASC( final SortedMap<String,Double> modeShareNow ){
		SortedMap<String, Double> ascs = new TreeMap<>();
		double lowestASC = Double.POSITIVE_INFINITY;
		
		for(Entry<String, Double> e: this.previousASC.entrySet()) {
			String mode = e.getKey();
			double asc = e.getValue() - Math.log(   modeShareNow.get(mode)  /  this.initialMode2share.get(mode) );
			ascs.put(mode, asc);
			lowestASC = Math.min(lowestASC, asc);
		}

		// number of non-zero ascs can not be greater than number of modes-1
		for(String mode :ascs.keySet()){
			double asc = ascs.get(mode) - lowestASC;
			this.previousASC.put(mode, asc);
			LOG.warn("The previous ASC for "+ mode +" was "+ this.previousASC.get(mode) +". It is changed to "+ asc);
		}
	}
}