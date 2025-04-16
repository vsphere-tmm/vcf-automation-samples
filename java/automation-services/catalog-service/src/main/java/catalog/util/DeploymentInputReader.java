package catalog.util;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

public class DeploymentInputReader {

    Map<String, String> serverConfig;

    public DeploymentInputReader() {
        String fileName = "catalog.yaml";  // File is under src/main/resources
        InputStream inputStream = DeploymentInputReader.class.getClassLoader().getResourceAsStream(fileName);
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(inputStream);
            serverConfig = (Map<String, String>) data.get("catalog");
        } catch (Exception e) {
            System.out.println(e.getLocalizedMessage());
        }
    }

    public String getProjectId() {
        return serverConfig.get("target_project_id");
    }

    public String getDeploymentName() {
        return serverConfig.get("deployment_name");
    }

    public String getCatalogItemId() {
        return serverConfig.get("catalog_item_id");
    }

    public String getflavorName() {
        return serverConfig.get("flavor_name");
    }

    public String getImageName() {
        return serverConfig.get("image_name");
    }

    public String getCloudZoneId() {
        return serverConfig.get("cloud_zone_id");
    }

}
