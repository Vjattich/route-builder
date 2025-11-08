import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;
import com.graphhopper.util.shapes.GHPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class Solver {

    private final GraphHopper hopper;

    public Solver(GraphHopper hopper) {
        this.hopper = hopper;
    }

    public List<Integer> solveOrder(List<GHPoint> points) {
        return solveTSP(buildDistanceMatrix(points));
    }

    //Make matrix for all points to all points and take best distance
    //n - size, cuz its n * n
    private double[][] buildDistanceMatrix(List<GHPoint> points) {
        int n = points.size();
        double[][] matrix = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    matrix[i][j] = 0;
                    continue;
                }
                GHResponse rsp = hopper.route(
                        new GHRequest(points.get(i), points.get(j))
                                .setProfile(Main.bike)
                                .setAlgorithm(Main.alghoritm)
                );
                matrix[i][j] = rsp.getBest().getDistance();
            }
        }
        return matrix;
    }

    private List<Integer> solveTSP(double[][] matrix) {

        var problem = VehicleRoutingProblem.Builder.newInstance()
                .addVehicle(
                        VehicleImpl.Builder.newInstance("vehicle")
                                .setStartLocation(com.graphhopper.jsprit.core.problem.Location.newInstance("0"))
                                .build()
                )
                .setRoutingCost(getVehicleRoutingTransportCosts(matrix))
                .setFleetSize(VehicleRoutingProblem.FleetSize.FINITE)
                .addAllJobs(
                        //Jobs for every point starts from 1
                        IntStream.range(1, matrix.length)
                                .mapToObj(i -> Service.Builder.newInstance("" + i)
                                        .setLocation(Location.newInstance(i))
                                        .build())
                                .toList()
                )
                .build();

        VehicleRoutingAlgorithm algorithm = Jsprit.createAlgorithm(problem);
        VehicleRoutingProblemSolution best = Solutions.bestOf(algorithm.searchSolutions());

        List<Integer> order = new ArrayList<>();
        order.add(0);

        best.getRoutes().forEach(route -> {
            for (TourActivity act : route.getActivities()) {
                order.add(Integer.parseInt(act.getLocation().getId()));
            }
        });

        return order;
    }


    //Add time for all points throuht matrix
    //time for speed 50 km\h
    private static VehicleRoutingTransportCosts getVehicleRoutingTransportCosts(double[][] matrix) {
        VehicleRoutingTransportCostsMatrix.Builder m = VehicleRoutingTransportCostsMatrix.Builder.newInstance(false);
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix.length; j++) {
                if (i == j) continue;
                double dist = matrix[i][j];
                double time = dist / (50_000.0 / 3600.0);
                m.addTransportDistance(String.valueOf(i), String.valueOf(j), dist);
                m.addTransportTime(String.valueOf(i), String.valueOf(j), time);
            }
        }
        return m.build();
    }

}
