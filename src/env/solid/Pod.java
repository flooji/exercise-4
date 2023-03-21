package solid;

import cartago.Artifact;
import cartago.OPERATION;
import cartago.OpFeedbackParam;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A CArtAgO artifact that agent can use to interact with LDP containers in a Solid pod.
 */
public class Pod extends Artifact {
    HttpClient client = HttpClient.newHttpClient();
    private static final String PATH_CONTAINER_TTL = "/Users/flo/IdeaProjects/exercise-4/src/resources/create_container.ttl";
    private String podURL; // the location of the Solid pod

  /**
   * Method called by CArtAgO to initialize the artifact.
   *
   * @param podURL The location of a Solid pod
   */
    public void init(String podURL) {
        this.podURL = podURL;
        log("Pod artifact initialized for: " + this.podURL);
    }

  /**
   * CArtAgO operation for creating a Linked Data Platform container in the Solid pod
   *
   * @param containerName The name of the container to be created
   *
   */
    @OPERATION
    public void createContainer(String containerName) {
        Path path_ttl = Paths.get(PATH_CONTAINER_TTL);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(podURL))
                    .header("Content-Type", "text/turtle")
                    .header("Link", "<http://www.w3.org/ns/ldp#BasicContainer>; rel=\"type\"")
                    .header("Slug", containerName + "/")
                    .POST(HttpRequest.BodyPublishers.ofFile(path_ttl))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(response);
        } catch (IOException | InterruptedException e) {
            log("Error: " + e.getMessage());
        }
    }

  /**
   * CArtAgO operation for publishing data within a .txt file in a Linked Data Platform container of the Solid pod
   *
   * @param containerName The name of the container where the .txt file resource will be created
   * @param fileName The name of the .txt file resource to be created in the container
   * @param data An array of Object data that will be stored in the .txt file
   */
    @OPERATION
    public void publishData(String containerName, String fileName, Object[] data) {
        String body = createStringFromArray(data);

        HttpRequest request = fileExists(containerName, fileName) ?
        requestForExistingFile(containerName, fileName, body) :
        requestForNewFile(containerName, fileName, body);

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log("Published data with status: " + response.statusCode());
        } catch (IOException | InterruptedException e) {
            log("Error: " + e.getMessage());
        }
    }

    private boolean fileExists(String containerName, String fileName) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(podURL + containerName + "/" + fileName))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return response.statusCode() != 404;
        } catch (IOException | InterruptedException e) {
            log("Error: " + e.getMessage());
            return false;
        }
    }

    private HttpRequest requestForExistingFile(String containerName, String fileName, String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(podURL + containerName + "/" + fileName))
                .header("Content-Type", "text/plain")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private HttpRequest requestForNewFile(String containerName, String fileName, String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(podURL + containerName))
                .header("Content-Type", "text/plain")
                .header("Slug", fileName)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    /**
   * CArtAgO operation for reading data of a .txt file in a Linked Data Platform container of the Solid pod
   *
   * @param containerName The name of the container where the .txt file resource is located
   * @param fileName The name of the .txt file resource that holds the data to be read
   * @param data An array whose elements are the data read from the .txt file
   */
    @OPERATION
    public void readData(String containerName, String fileName, OpFeedbackParam<Object[]> data) {
        Object[] responseObjects = makeReadDataRequest(containerName, fileName);

        data.set(responseObjects);
    }

    /**
   * Method for reading data of a .txt file in a Linked Data Platform container of the Solid pod
   *
   * @param containerName The name of the container where the .txt file resource is located
   * @param fileName The name of the .txt file resource that holds the data to be read
   * @return An array whose elements are the data read from the .txt file
   */
    public Object[] readData(String containerName, String fileName) {
        return makeReadDataRequest(containerName, fileName);
    }

    private Object[] makeReadDataRequest(String containerName, String fileName) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(podURL + containerName + "/" + fileName))
                .GET()
                .build();

        HttpResponse<String> response;
        Object[] responseObjects;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            responseObjects = createArrayFromString(response.body());
            log("Read data request: " + response.statusCode());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return responseObjects;
    }


    /**
   * Method that converts an array of Object instances to a string, 
   * e.g. the array ["one", 2, true] is converted to the string "one\n2\ntrue\n"
   *
   * @param array The array to be converted to a string
   * @return A string consisting of the string values of the array elements separated by "\n"
   */
    public static String createStringFromArray(Object[] array) {
        StringBuilder sb = new StringBuilder();
        for (Object obj : array) {
            sb.append(obj.toString()).append("\n");
        }
        return sb.toString();
    }

  /**
   * Method that converts a string to an array of Object instances computed by splitting the given string with delimiter "\n"
   * e.g. the string "one\n2\ntrue\n" is converted to the array ["one", "2", "true"]
   *
   * @param str The string to be converted to an array
   * @return An array consisting of string values that occur by splitting the string around "\n"
   */
    public static Object[] createArrayFromString(String str) {
        return str.split("\n");
    }


  /**
   * CArtAgO operation for updating data of a .txt file in a Linked Data Platform container of the Solid pod
   * The method reads the data currently stored in the .txt file and publishes in the file the old data along with new data 
   * 
   * @param containerName The name of the container where the .txt file resource is located
   * @param fileName The name of the .txt file resource that holds the data to be updated
   * @param data An array whose elements are the new data to be added in the .txt file
   */
    @OPERATION
    public void updateData(String containerName, String fileName, Object[] data) {
        Object[] oldData = readData(containerName, fileName);
        Object[] allData = new Object[oldData.length + data.length];
        System.arraycopy(oldData, 0, allData, 0, oldData.length);
        System.arraycopy(data, 0, allData, oldData.length, data.length);
        publishData(containerName, fileName, allData);
    }
}
