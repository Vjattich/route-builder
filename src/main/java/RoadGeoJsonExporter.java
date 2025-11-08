import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import org.geojson.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RoadGeoJsonExporter {

    private final ObjectMapper objectMapper;
    private final GraphHopper hopper;

    public RoadGeoJsonExporter(ObjectMapper objectMapper,
                               GraphHopper hopper) {
        this.objectMapper = objectMapper;
        this.hopper = hopper;
    }

    public void writeRoadRouteGeoJson(FeatureCollection points,
                                      List<GHPoint> ghPoints,
                                      List<Integer> pointsOrder,
                                      boolean roundTrip,
                                      String outFile) throws Exception {

        List<LineString> legGeoms = new ArrayList<>();

        int legs = Math.max(0, pointsOrder.size() - 1);
        for (int k = 0; k < legs; k++) {
            addLegFeature(ghPoints.get(pointsOrder.get(k)), ghPoints.get(pointsOrder.get(k + 1)), points, legGeoms);
        }

        if (roundTrip && pointsOrder.size() > 1) {
            addLegFeature(ghPoints.get(pointsOrder.getLast()), ghPoints.get(pointsOrder.getFirst()), points, legGeoms);
        }

        if (legGeoms.isEmpty()) {
            throw new IllegalArgumentException("legGeoms is empty");
        }

        MultiLineString mls = new MultiLineString();
        for (LineString ls : legGeoms) mls.add(ls.getCoordinates());
        Feature trip = new Feature();
        trip.setGeometry(mls);
        trip.setProperty("type", "trip");
        trip.setProperty("profile", Main.bike);
        trip.setProperty("roundTrip", roundTrip);
        trip.setProperty("ordered_indices", pointsOrder);
        points.add(trip);

        File resultFile = new File(outFile);
        objectMapper.writeValue(resultFile, points);

        IO.println("Result: " + resultFile.getAbsolutePath());
    }

    private void addLegFeature(GHPoint a,
                               GHPoint b,
                               FeatureCollection fc,
                               List<LineString> legGeoms) {

        GHResponse rsp = hopper.route(
                new GHRequest(a, b)
                        .setProfile(Main.bike)
                        .setAlgorithm(Main.alghoritm)
        );

        if (rsp.hasErrors()) {
            throw new RuntimeException("Routing error for leg " + rsp.getErrors());
        }

        ResponsePath path = rsp.getBest();
        PointList pl = path.getPoints();

        // Convert GH polyline to GeoJSON LineString
        List<LngLatAlt> coords = new ArrayList<>(pl.size());
        for (int idx = 0; idx < pl.size(); idx++) {
            coords.add(new LngLatAlt(pl.getLon(idx), pl.getLat(idx)));
        }
        LineString line = new LineString(coords.toArray(new LngLatAlt[0]));
        legGeoms.add(line);

        // Per-leg feature with properties
        Feature f = new Feature();
        f.setGeometry(line);
        f.setProperty("type", "leg");
        f.setProperty("distance_m", path.getDistance());
        f.setProperty("ascend_m", path.getAscend());
        f.setProperty("descend_m", path.getDescend());
        fc.add(f);
    }
}
