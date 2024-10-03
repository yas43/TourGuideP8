package com.openclassrooms.tourguide.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NearByAttraction {
    String touristAttractionName;
    Double touristAttractionLat;
    Double touristAttractionLong;
    Double distance;
    Integer rewardPoint;
}
