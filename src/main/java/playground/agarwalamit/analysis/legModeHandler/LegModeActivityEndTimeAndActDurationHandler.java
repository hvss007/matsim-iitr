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
package playground.agarwalamit.analysis.legModeHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonStuckEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.gbl.Gbl;

/**
 * Handles activity end and start events and returns the leg mode distribution data for activity end and activity duration.
 * @author amit
 */

public class LegModeActivityEndTimeAndActDurationHandler implements PersonDepartureEventHandler, 
ActivityEndEventHandler, ActivityStartEventHandler, PersonStuckEventHandler {

	private final Logger logger = Logger.getLogger(LegModeActivityEndTimeAndActDurationHandler.class);
	private SortedMap<String, Map<Id<Person>, List<Double>>> mode2PersonId2ActEndTimes;
	private SortedMap<String, Map<Id<Person>, List<Double>>> mode2PersonId2ActDurations;
	private Map<Id<Person>, SortedMap<String, Double>> personId2ActEndTimes;
	private Map<Id<Person>, SortedMap<String, Double>> personId2ActStartTimes;
	private Set<Id<Person>> stuckPersons;
	private Map<Id<Person>, String> personId2LegModes;
	private Scenario sc;
	private final int maxStuckAndAbortWarnCount=5;
	private int warnCount = 0;
	private double simEndTime;

	/**
	 * @param scenario should contain config details and population file.
	 */
	public LegModeActivityEndTimeAndActDurationHandler(Scenario scenario) {
		this.logger.warn("Leg mode 2 person id 2 activity endTime/StartTime/duration will work fine if all trips of a person are made by same travel mode. "
				+ "Because, travel mode is linked with departure events not with activity end or start events.");
		this.sc = scenario;
		this.simEndTime = sc.getConfig().qsim().getEndTime();
		this.mode2PersonId2ActEndTimes = new TreeMap<String, Map<Id<Person>,List<Double>>>();
		this.mode2PersonId2ActDurations = new TreeMap<String, Map<Id<Person>,List<Double>>>();
		this.personId2ActEndTimes = new HashMap<Id<Person>, SortedMap<String,Double>>();
		this.personId2LegModes = new HashMap<Id<Person>, String>();
		this.personId2ActStartTimes = new HashMap<Id<Person>, SortedMap<String,Double>>();
		this.stuckPersons = new HashSet<Id<Person>>();

		for(Person p:this.sc.getPopulation().getPersons().values()){
			Id<Person> id = p.getId();
			this.personId2ActEndTimes.put(id, new TreeMap<String, Double>());
			this.personId2ActStartTimes.put(id, new TreeMap<String, Double>());
		}
	}

	@Override
	public void reset(int iteration) {
		this.mode2PersonId2ActEndTimes.clear();
		this.personId2ActEndTimes.clear();
		this.personId2LegModes.clear();
		this.personId2ActStartTimes.clear();
		this.mode2PersonId2ActDurations.clear();
		this.stuckPersons.clear();
	}

	@Override
	public void handleEvent(ActivityEndEvent event) {
		Id<Person> personId = event.getPersonId();
		double actEndTime = event.getTime();

		SortedMap<String,Double> actEndTimes = this.personId2ActEndTimes.get(personId);
		actEndTimes.put(event.getActType(),actEndTime);
	}

	@Override
	public void handleEvent(ActivityStartEvent event) {
		Id<Person> personId = event.getPersonId();
		double actStartTime = event.getTime();

		SortedMap<String, Double>  actStartTimes = this.personId2ActStartTimes.get(personId);
		actStartTimes.put(event.getActType(),actStartTime);
	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {
		this.personId2LegModes.put(event.getPersonId(), event.getLegMode());
	}

	@Override
	public void handleEvent(PersonStuckEvent event) {
		this.stuckPersons.add(event.getPersonId());
		this.warnCount++;
		if(this.warnCount<=this.maxStuckAndAbortWarnCount){
			this.logger.warn("'StuckAndAbort' event is thrown for person "+event.getPersonId().toString()+" Details are "+event.toString()+
					". \n Correctness of travel durations for such persons can not be guaranteed.");
			if(this.warnCount==this.maxStuckAndAbortWarnCount) this.logger.warn(Gbl.FUTURE_SUPPRESSED);
		}
	}

	public SortedMap<String, Map<Id<Person>, List<Double>>> getLegMode2PesonId2ActEndTimes (){
		this.mode2PersonId2ActEndTimes= sortingPersonWRTMode(this.personId2ActEndTimes);
		return this.mode2PersonId2ActEndTimes;
	}

	public SortedMap<String, Map<Id<Person>, List<Double>>> getLegMode2PesonId2ActDurations (){
		Map<Id<Person>, SortedMap<String,Double>> personId2ActDurations = getPersonId2ActType2ActDurations();
		this.mode2PersonId2ActDurations = sortingPersonWRTMode(personId2ActDurations);
		return this.mode2PersonId2ActDurations;
	}

	private SortedMap<String, Map<Id<Person>, List<Double>>> sortingPersonWRTMode(Map<Id<Person>, SortedMap<String,Double>> pId2Times){
		SortedMap<String, Map<Id<Person>, List<Double>>> mode2PersonId2Times = new TreeMap<String, Map<Id<Person>,List<Double>>>();

		for(String travelMode :this.personId2LegModes.values()){
			Map<Id<Person>, List<Double>> localPId2times = new HashMap<Id<Person>, List<Double>>();
			mode2PersonId2Times.put(travelMode, localPId2times);
		}

		for(Id<Person> id:pId2Times.keySet()){
			String mode = this.personId2LegModes.get(id);
			Map<Id<Person>, List<Double>> localPersonId2ActTimes = mode2PersonId2Times.get(mode);
			localPersonId2ActTimes.put(id, new ArrayList<Double>(pId2Times.get(id).values()));
		}
		return mode2PersonId2Times;
	}

	private void checkForActivityDuration(SortedMap<String,Double> actDurations){
		for(double d :actDurations.values()){
			if(d<0) throw new RuntimeException("Activity duration is negative. Aborting...");
			else if(d==0) {
				this.logger.warn("Activity duration is zero, it means activity start and end times are same. Do check for consistency.");
			}
			else if(d>=this.simEndTime) this.logger.warn("Activity duration is more than simulation end time. Do check for consistency.");
		}
	}
	public Map<Id<Person>, SortedMap<String,Double>> getPersonId2ActType2ActDurations(){
		if(this.warnCount>0) this.logger.warn(this.warnCount+" 'StuckAndAbort' events are thrown. "
				+ "Correctness of travel durations for stuck persons can not be guaranteed. Therefore, excluding such agents in the calculations.");
		Map<Id<Person>, SortedMap<String,Double>> personId2Durations = new HashMap<Id<Person>, SortedMap<String,Double>>();
		for(Id<Person> id:this.personId2ActEndTimes.keySet()){
			if(!stuckPersons.contains(id)){
				SortedMap<String,Double> actEndTimes = this.personId2ActEndTimes.get(id);
				SortedMap<String,Double> actStartTimes = this.personId2ActStartTimes.get(id);
				SortedMap<String,Double> actDurations = new TreeMap<String, Double>();
				for(String act : actEndTimes.keySet()){
					double dur = actEndTimes.get(act)-actStartTimes.get(act);
					if(dur<0) dur=dur+this.simEndTime;
					actDurations.put(act, dur);
				}
				checkForActivityDuration(actDurations);
				personId2Durations.put(id,actDurations);
			}
		}
		return personId2Durations;
	}
}
