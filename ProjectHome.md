# Project overview #
Temporal JDBC Proxy allows to keep transactional time and history of data with a two-timestamps solutions. The proxy accepts SQL queries with temporal extensions and rewrites them into standards SQL seemingly for the supported DBMSs.

The software assumes a schema which has been temporalized. Temporalizing a schema basically means adding the needed information to keep history of data, namely
the timestamps, also appends tend column to all primary keys.

This implementation is based on the [Log4JDBC](http://code.google.com/p/log4jdbc/).

## Dependencies ##
The Temporal JDBC Proxy uses the [TSQLParser](http://code.google.com/p/tsqlparser/), a separate project for parsing TSQL queries.

## Usage and configuration ##
The difference with a standard JDBC is the connection which is returned by:

> ```sql
Connection conn= TemporalDriverManager.getConnection(driver, username, password, schema); ```

Where schema is a "temporalized" schema (see Temporalize Utility)

Note: For MySQL, you need to compile and install the now\_usec() UDF (see Add-on)
  1. Compile the UDF:
> ```sql
gcc -fPIC -Wall -I/usr/include/mysql -shared -o now_usec.so now_usec.cc```
  1. Move the now\_usec.so to mysql plugin directory
  1. Create the function in mysql:
> ```shell
mysql> create function now_usec returns string soname 'now_usec.so'; ```
  1. Test the the function:
> ```shell
mysql> select now_usec();```

## Temporalize Utility ##
This version includes a utility to "temporized" a schema, which consists of:
  * Add two columns (tstart, tend) to each table.
  * tend column is appended to all primary keys.

## Limitations ##
  * The current version support MySQL and PostgreSQL.
  * For practicality reasons the level of granularity is the microseconds, ie: for two txns on the same row and withing the same microsec, only the first is kept.
  * Timestamps in MySQL are in a decimal format.
  * A now\_usec() UDF needs to be added to MySQL.
  * batch statements are not supported.

## Version ##
Temporal JDBC Proxy 0.1 (Alpha)

## Contact ##
Carlo Curino

## License and Restrictions ##

The Temporal JDBC Proxy is a free software released under the Apache v.2.0 license.