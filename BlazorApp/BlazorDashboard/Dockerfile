FROM microsoft/dotnet:2.1-sdk AS build-env
WORKDIR /app

# build in container
COPY . ./
RUN cd BlazorDashboard.Server && dotnet publish -c Release -o out -r linux-x64

#build 
FROM microsoft/dotnet:2.1-runtime
WORKDIR /app
COPY --from=build-env /app/BlazorDashboard.Server/out ./
RUN apt-get update
RUN apt-get install libunwind8
ENTRYPOINT ["dotnet", "BlazorDashboard.Server.dll"]

