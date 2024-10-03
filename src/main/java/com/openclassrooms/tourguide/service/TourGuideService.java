package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.model.NearByAttraction;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.model.User;
import com.openclassrooms.tourguide.model.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import rewardCentral.RewardCentral;
import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	private final RewardCentral rewardsCentral = new RewardCentral();
	boolean testMode = true;
	private final ExecutorService executorService = Executors.newFixedThreadPool(2);

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;


		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
//		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
//				: trackUserLocation(user);
//
		if (user.getVisitedLocations().isEmpty()) {
			try {
				return trackUserLocation(user).get();
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
				return null;
			}
		} else {
			return user.getLastVisitedLocation();
		}
	}

	public List<NearByAttraction> getNearByAttraction(User user) {
		//		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
		//				: trackUserLocation(user);
		//		return visitedLocation;


		List<NearByAttraction>attractionListOffer = new LinkedList<>();
		VisitedLocation lastVisitedLocation = user.getLastVisitedLocation();
		Map<Attraction,Double> attractionList = gpsUtil.getAttractions().stream()
				.collect(Collectors.toMap(r->r,r->rewardsService.getDistance(r,lastVisitedLocation.location)));
		attractionList.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(5)
				.forEach(r->{
					NearByAttraction nearByAttraction = new NearByAttraction();
					nearByAttraction.setTouristAttractionName(r.getKey().attractionName);
					nearByAttraction.setTouristAttractionLat(r.getKey().latitude);
					nearByAttraction.setTouristAttractionLong(r.getKey().longitude);
					nearByAttraction.setDistance(r.getValue());
					nearByAttraction.setRewardPoint(rewardsCentral.getAttractionRewardPoints(r.getKey().attractionId,user.getUserId()));
					attractionListOffer.add(nearByAttraction);
				});
		return attractionListOffer;

	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	public CompletableFuture<VisitedLocation> trackUserLocation(User user) {
//		VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
//		user.addToVisitedLocations(visitedLocation);
//		rewardsService.calculateRewards(user);
//		return visitedLocation;

		return CompletableFuture.supplyAsync(() -> {
			VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
			user.addToVisitedLocations(visitedLocation);
			rewardsService.calculateRewards(user);
			return visitedLocation;
		}, executorService);
	}

	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
		List<Attraction> nearbyAttractions = new ArrayList<>();
		Map<Attraction,Double> map = (Map<Attraction, Double>) gpsUtil.getAttractions().stream()
				.collect(Collectors.toMap(r->r,r->rewardsService.getDistance(r,visitedLocation.location)));

	 	map.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(5)
				.forEach(r->nearbyAttractions.add(r.getKey()));
//				.collect(Collectors.toList());
		return nearbyAttractions;
	}

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
			}
		});
	}

	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);
//			generateRandomUserReward(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

	private Attraction generateRandomAttraction() {
	List<Attraction> attractionsList = gpsUtil.getAttractions();
		Random r = new Random();
		int low = 0;
		int high = 26;
		int random = r.nextInt(high-low) + low;

		return attractionsList.get(random);
	}


	private int generateRandomRewardPoint(){
		Random r = new Random();
		int low = 1;
		int high = 10;
		int random = r.nextInt(high-low) + low;

		return random;
	}

	private void generateRandomUserReward(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addUserReward(new UserReward(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime())
					,generateRandomAttraction(),
					generateRandomRewardPoint()));
		});
	}

}
