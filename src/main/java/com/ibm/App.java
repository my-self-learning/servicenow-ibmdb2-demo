/*
 * Created on Wed Mar 02 2020
 *
 * Copyright (c) 2020 IBM
 * @author Aashish Chaubey
 * @email chaubey.aashish@gmail.com
 */

/**
 * Before running the program, ensure:
 * 1. The ServiceNow instance is not sleeping, else wake up the instance from the dashboard
 * 2. Check if there is a DB2 Database provisioned and enter the details in the config file
 */

package com.ibm;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import org.json.JSONException;
import java.net.URISyntaxException;
import java.sql.*;
import java.util.Properties;

import org.json.*;

public class App {

    static {
        try {
            /**
             * Load the DB2 driver
             */
            Class.forName("com.ibm.db2.jcc.DB2Driver");
            System.out.println("**** Loaded the JDBC driver");
        } catch (ClassNotFoundException e) {
            System.err.println("Could not load JDBC driver");
            e.printStackTrace();
        }
    }

    Properties properties = new Properties();

    private String url;
    private String user;
    private String password;

    public App() throws IOException {
        String propFileName = "config.properties";
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);

        if (inputStream != null) {
            properties.load(inputStream);
        } else {
            throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
        }

        this.url = properties.getProperty("db2_url");
        this.user = properties.getProperty("db2_user");
        this.password = properties.getProperty("db2_password");
    }

    public static void main(String[] args) throws IOException, HttpException {
        App restAction = new App();
        restAction.getRequest();
    }

    public void getRequest() throws HttpException, IOException {
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(new HttpHost(properties.getProperty("servicenow_instance"))),
                new UsernamePasswordCredentials(properties.getProperty("servicenow_user"),
                        properties.getProperty("servicenow_password")));
        CloseableHttpClient httpclient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider)
                .build();

        try {
            URIBuilder builder = new URIBuilder(properties.getProperty("servicenow_api") + "/table/incident");
            builder.setParameter("sysparm_query",
                    "sys_created_onBETWEENjavascript:gs.dateGenerate('2019-12-16','23:59:59')@javascript:gs.dateGenerate('2020-02-16','23:59:59')")
                    .setParameter("sysparm_limit", "10").setParameter("sysparm_query", "ORDERBYDESCsys_updated_on");
            HttpGet httpget = new HttpGet(builder.build());
            httpget.setHeader("Accept", "application/json");
            System.out.println("Executing request " + httpget.getRequestLine());
            CloseableHttpResponse response = httpclient.execute(httpget);
            try {
                System.out.println("----------------------------------------");
                System.out.println(response.getStatusLine());
                final String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("------------ Data Fetched --------------");
                write2DB2(responseBody);
            } finally {
                response.close();
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } finally {
            httpclient.close();
        }
    }

    /**
     * Write the data to the DB2 database
     * @param data
     */
    public void write2DB2(String data) {
        ResultSet rs = null;
        try {
            /**
             * Create the connection using the IBM Data Server Driver for JDBC and SQLJ and
             * commit changes manually
             */
            Connection con = DriverManager.getConnection(this.url, this.user, this.password);
            // con.setAutoCommit(false);
            System.out.println("**** Created a JDBC connection to the data source: " + con.getMetaData().getDatabaseProductName());

            /**
             * Create the Statement object
             */
            Statement stmt = con.createStatement();

            DatabaseMetaData dbm = con.getMetaData();
            rs = dbm.getTables(null, "WRQ18903", "TEST_TABLE", null);
            if (rs.next()) {
                System.out.println("**** Table exists...");
            } else {
                System.out.println("**** Table does not exist...");
                /**
                 * Create table in db2 database
                 */
                stmt.executeUpdate("CREATE TABLE WRQ18903.TEST_TABLE(number VARCHAR(10), sys_updated_on DATE)");
                System.out.println("Table created!");
            }

            /**
             * Creating a JSON Object to parse through the data
             */
            JSONObject jsonObject = new JSONObject(data);

            for (int i = 0; i < jsonObject.getJSONArray("result").length(); i++) {
                stmt.executeUpdate(String.format("INSERT INTO WRQ18903.TEST_TABLE VALUES('%s', '%s')",
                    jsonObject.getJSONArray("result").getJSONObject(i).getString("number"),
                    jsonObject.getJSONArray("result").getJSONObject(i).getString("sys_updated_on")));
            }
            System.out.println("************ Values Inserted");
            stmt.close();
            rs.close();

        } catch (SQLException sqlex) {
            System.err.println("SQLException information");
            while (sqlex != null) {
                System.err.println("Error msg: " + sqlex.getMessage());
                System.err.println("SQLSTATE: " + sqlex.getSQLState());
                System.err.println("Error code: " + sqlex.getErrorCode());
                sqlex.printStackTrace();
                sqlex = sqlex.getNextException();
            }
        } catch (JSONException jsonException) {
            System.err.println("JSONExceptopm Found");
            System.out.println("****** Check if the ServiceNow instance is awake in the console");
            jsonException.printStackTrace();
        }
    }
}
