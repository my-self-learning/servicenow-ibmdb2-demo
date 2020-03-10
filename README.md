# ServiceNow with DB2

*Using the custom ServiceNow application*

## How to run this app

- Create a `config.properties` file in the resources folder

    /src/main/resources/config.properties

    db2_url=<`Your IBM DB2 JDBC url`>  
    db2_password=<`Your IBM DB2 password`>  
    servicenow_instance=<`Your ServiceNow instance domain`>  
    servicenow_user=<`Your ServiceNow instance username`>  
    servicenow_password=<`Your ServiceNow instance password`>  
    servicenow_api=<`Your ServiceNow instance database API`>  

- Check if the ServiceNow instance is not sleeping, else wake it up from the dashboard

- Run the `App.java` file