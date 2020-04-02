package be.vito.terrascope.snapgpt;

import java.io.Serializable;
import java.util.Map;

public class STACProduct implements Serializable {

    String id;
    Map<String,Object> properties;
    Object geometry;
    Map<String, STACProduct> inputs;
    Map<String, Map<String,String>> assets;
}
