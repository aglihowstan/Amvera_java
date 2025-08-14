import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Main {

    public static void main(String[] args) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            String apiKey = "54bd5528-8297-484b-882b-6067fe96e360";
            String address = URLEncoder.encode("Москва, Красная площадь", StandardCharsets.UTF_8);
            String url = "https://geocode-maps.yandex.ru/1.x?geocode=" + address + "&format=json&apikey=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Ошибка: " + response.body());
                return;
            }
            String json = response.body();
            ObjectMapper mapper = new ObjectMapper();
            YandexGeocodeResponse resp = mapper.readValue(json, YandexGeocodeResponse.class);

            if (resp != null &&
                    resp.response != null &&
                    resp.response.GeoObjectCollection != null &&
                    resp.response.GeoObjectCollection.featureMember != null &&
                    resp.response.GeoObjectCollection.featureMember.length > 0) {

                FeatureMember member = resp.response.GeoObjectCollection.featureMember[0];
                if (member.GeoObject != null && member.GeoObject.Point != null && member.GeoObject.Point.pos != null) {
                    String[] coords = member.GeoObject.Point.pos.split(" ");
                    if (coords.length == 2) {
                        System.out.println("Координаты: " + coords[1] + ", " + coords[0]);
                        return;
                    }
                }
            }
            System.out.println("Не удалось извлечь координаты из ответа");
            System.out.println("Полный ответ: " + json);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class YandexGeocodeResponse {
        public Response response;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Response {
        public GeoObjectCollection GeoObjectCollection;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GeoObjectCollection {
        public FeatureMember[] featureMember;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class FeatureMember {
        public GeoObject GeoObject;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GeoObject {
        public Point Point;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Point {
        public String pos;
    }
}