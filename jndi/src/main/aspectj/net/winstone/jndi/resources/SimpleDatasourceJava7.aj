package net.winstone.jndi.resources;


public privileged aspect SimpleDatasourceJava7 {

    /**
     * getParentLogger() was added to JDBC in Java 1.7. Always throws  SQLFeatureNotSupportedException, intended approach to use according to docs when your implementation does not use a logger.
     *
     * @return logger
     * @throws SQLFeatureNotSupportedException
     */
    public Logger SimpleDatasource.getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
}
