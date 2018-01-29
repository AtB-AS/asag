/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package org.entur.asag.mapbox.mapper;

import org.geojson.Feature;
import org.rutebanken.netex.model.StopPlace;
import org.rutebanken.netex.model.StopPlace_VersionStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static org.entur.asag.mapbox.mapper.KeyValuesHelper.getValueByKey;
import static org.entur.asag.mapbox.mapper.MapperHelper.setIfNotNull;
import static org.entur.asag.mapbox.mapper.MapperHelper.setResolvedValue;

/**
 * Mapping of netex stop place to geojson.
 * NOTE THAT: General mapping for Zone is not done here.
 */
@Service
public class StopPlaceToGeoJsonFeatureMapper {

    static final String NETEX_IS_PARENT_STOP_PLACE = "IS_PARENT_STOP_PLACE";
    static final String FINAL_STOP_PLACE_TYPE = "finalStopPlaceType";
    static final String SUBMODE = "submode";
    static final String PUBLIC_CODE = "publicCode";
    static final String STOP_PLACE_TYPE = "stopPlaceType";
    static final String WEIGHTING = "weighting";
    static final String HAS_PARENT_SITE_REF = "hasParentSiteRef";
    static final String IS_PARENT_STOP_PLACE = "isParentStopPlace";
    private static final Logger logger = LoggerFactory.getLogger(StopPlaceToGeoJsonFeatureMapper.class);
    private final ZoneToGeoJsonFeatureMapper zoneToGeoJsonFeatureMapper;

    @Autowired
    public StopPlaceToGeoJsonFeatureMapper(ZoneToGeoJsonFeatureMapper zoneToGeoJsonFeatureMapper) {
        this.zoneToGeoJsonFeatureMapper = zoneToGeoJsonFeatureMapper;
    }

    public Feature mapStopPlaceToGeoJson(StopPlace stopPlace) {
        Feature feature = zoneToGeoJsonFeatureMapper.mapZoneToGeoJson(stopPlace);

        Optional<String> optionalSubmode = resolveFirstSubmodeToSingleValue(stopPlace);

        optionalSubmode.ifPresent(submode -> {
            feature.setProperty(SUBMODE, submode);
            feature.setProperty(FINAL_STOP_PLACE_TYPE, submode);
        });

        if (stopPlace.getStopPlaceType() != null) {
            feature.setProperty(STOP_PLACE_TYPE, stopPlace.getStopPlaceType().value());
            if (!optionalSubmode.isPresent()) {
                feature.setProperty(FINAL_STOP_PLACE_TYPE, stopPlace.getStopPlaceType().value());
            }
        }

        setIfNotNull(PUBLIC_CODE, stopPlace.getPublicCode(), feature::setProperty);
        setResolvedValue(WEIGHTING, stopPlace.getWeighting(), feature::setProperty);
        feature.setProperty(HAS_PARENT_SITE_REF, String.valueOf(stopPlace.getParentSiteRef() != null));
        getValueByKey(stopPlace, NETEX_IS_PARENT_STOP_PLACE).ifPresent(isParent -> feature.setProperty(IS_PARENT_STOP_PLACE, isParent));

        return feature;

    }

    private Optional<String> resolveFirstSubmodeToSingleValue(StopPlace stopPlace) {
        return Arrays.stream(StopPlace_VersionStructure.class.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("get") && method.getName().endsWith("Submode"))
                .map(method -> safeInvoke(method, stopPlace))
                .filter(Objects::nonNull)
                .map(MapperHelper::getEnumValue)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(value -> !"unknown".equals(value))
                .findAny();
    }


    private Object safeInvoke(Method method, StopPlace stopPlace) {
        try {
            return method.invoke(stopPlace);
        } catch (InvocationTargetException | IllegalAccessException e) {
            logger.warn("Error resolving submode from stop place {}. Ignoring this method: {}", stopPlace.getId(), method.getName(), e);
        }
        return null;
    }


}
