import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.json.Statement;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.shapes.GHPoint;
import org.geojson.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.isNull;

public class Main {

    private static GraphHopper hopper;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static final String bike = "bike";
    public static final String alghoritm = "dijkstrabi";

    public static void main(String[] args) throws Exception {

        String map = args[0];
        String pointsPath = args[1];

        if (isNull(map) || isNull(pointsPath)) {
            throw new IllegalArgumentException("Missing parameters");
        }

        IO.println("Map: " + map);
        IO.println("Poitns: " + pointsPath);

        hopper = loadGrassHopper(map);

        FeatureCollection points = loadPoints(pointsPath);
        List<GHPoint> ghPoints = toGHPoints(points);

        RoadGeoJsonExporter roadGeoJsonExporter = new RoadGeoJsonExporter(
                objectMapper,
                hopper
        );

        roadGeoJsonExporter.writeRoadRouteGeoJson(
                points,
                ghPoints,
                new Solver(hopper).solveOrder(ghPoints),
                true,
                "route.geojson"
        );
    }

    private static FeatureCollection loadPoints(String pointsPath) throws IOException {
        return objectMapper.readValue(new File(pointsPath), FeatureCollection.class);
    }

    private static GraphHopper loadGrassHopper(String osmFile) {
        return new GraphHopper()
                .setOSMFile(osmFile)
                .setGraphHopperLocation("graph-cache")
                .setEncodedValuesString("road_class")
                .setProfiles(
                        new Profile(Main.bike)
                                .setName(Main.bike)
                                .setWeighting("custom")
                                .setCustomModel(
                                        new CustomModel()
                                                .addToSpeed(Statement.If("road_class == PRIMARY", Statement.Op.LIMIT, "40"))
                                                .addToSpeed(Statement.Else(Statement.Op.LIMIT, "50"))
                                )
                )
                .importOrLoad();
    }


    public static List<GHPoint> toGHPoints(FeatureCollection features) {

        if (isNull(features) || features.getFeatures().isEmpty()) {
            throw new IllegalArgumentException("features or features is null");
        }

        List<GHPoint> out = new ArrayList<>();

        for (Feature f : features.getFeatures()) {

            if (isNull(f) || isNull(f.getGeometry())) {
                continue;
            }

            if (f.getGeometry() instanceof Point p) {
                LngLatAlt c = p.getCoordinates();
                if (isNull(c)) {
                    continue;
                }
                out.add(new GHPoint(c.getLatitude(), c.getLongitude()));
                continue;
            }

            if (f.getGeometry() instanceof MultiPoint mp) {
                for (LngLatAlt c : mp.getCoordinates()) {
                    if (isNull(c)) {
                        continue;
                    }
                    out.add(new GHPoint(c.getLatitude(), c.getLongitude()));
                }
                continue;
            }

            throw new IllegalArgumentException("feature type not supported: " + f.getGeometry().getClass());
        }

        return out;
    }

}
