package com.example.demo.template;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SoapRequestBodyXml {

    public static String ListOfContinentsByName = """
<?xml version="1.0" encoding="utf-8"?>
<soap12:Envelope xmlns:soap12="http://www.w3.org/2003/05/soap-envelope">
  <soap12:Body>
    <ListOfContinentsByName xmlns="http://www.oorsprong.org/websamples.countryinfo">
    </ListOfContinentsByName>
  </soap12:Body>
</soap12:Envelope>
            """;

    public static Map<String, String> map;
    static {
        map = Stream.of(new String[][]{
                {"ListOfContinentsByName", ListOfContinentsByName}
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
    }

}
