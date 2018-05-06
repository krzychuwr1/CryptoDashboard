# CryptoDashboard
Web App with a dashboard displaying some cryptocurrencies data, using blazor on frontend

# Deployment

Requirements: Jdk 1.8, sbt 1.1, docker (version 17.06 or higher)
Or instructions are assuming that starting directory is root of the project

What is where:

* Dashboard is available at /
* API is available at /api/<version>
* SwaggerUI is available at /api

## Build steps

### 1. Build server image

```
$> cd ./server
$> sbt
$sbt:server> docker
```
This should create image named dashboard_server

### 2. Build dashboard app image

```
$> cd ./BlazorApp/BlazorDashboard
$> docker build -t dashboard_blazor:latest .
```

This should build image for blazor dashboard

### 3. Build proxy

```
$> cd ./docker/proxy
$> docker build -t dashboard_reverseproxy .
```

### 4. Run application

```
$> cd ./docker
$> docker-compose up (-d)
```
Application should be up and running now

