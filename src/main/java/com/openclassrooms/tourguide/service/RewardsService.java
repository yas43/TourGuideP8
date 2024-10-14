package com.openclassrooms.tourguide.service;

import java.util.*;
import java.util.concurrent.*;

import lombok.*;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.model.User;
import com.openclassrooms.tourguide.model.UserReward;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	ExecutorService service = Executors.newFixedThreadPool(1000);
	
	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}

	public int getProximityBuffer() {
		return proximityBuffer;
	}

	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}
	
	public CompletableFuture<Void> calculateRewards(User user) {
		return
				CompletableFuture.runAsync(()-> {
					List<VisitedLocation> userLocations = new ArrayList<>(user.getVisitedLocations());
					List<Attraction> attractions = gpsUtil.getAttractions();
					CopyOnWriteArrayList<UserReward> userRewards = new CopyOnWriteArrayList<>(user.getUserRewards());


					for (VisitedLocation visitedLocation : userLocations) {
						for (Attraction attraction : attractions) {
							boolean notRewarded = userRewards.stream()
									.noneMatch(r -> r.attraction.attractionName.equals(attraction.attractionName));

							if (nearAttraction(visitedLocation, attraction) && notRewarded) {
								UserReward newUserReward = new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user));
								userRewards.add(newUserReward);
							}
						}
					}
					user.setUserRewards(userRewards);
				},service);

	}
	
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}
	
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}
	
	private int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}
	
	public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
	}

}
