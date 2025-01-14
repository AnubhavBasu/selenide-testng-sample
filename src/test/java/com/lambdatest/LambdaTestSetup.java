package com.lambdatest;

import java.io.FileReader;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.common.net.MediaType;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Parameters;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.codeborne.selenide.WebDriverRunner;

public class LambdaTestSetup {
	public RemoteWebDriver driver;
	public String status = "failed";

	public static String username;
	public static String accessKey;
	public static String sessionId;

	protected void fetchSessionDetails(String sessionId) {
		try {
			String url = "sessions/";
			JSONObject sessionDetails = getSessionDetails(url,sessionId);
			if (sessionDetails != null) {
				JSONObject data = (JSONObject) sessionDetails.get("data");
                System.out.println("Logs: " + data.get("logs"));
				System.out.println("Test ID: " + data.get("test_id"));
				System.out.println("Build ID: " + data.get("build_id"));
				url = "builds/";
				JSONObject buildDetails = getSessionDetails(url,data.get("build_id").toString());
				JSONObject buildData = (JSONObject) buildDetails.get("data");
				System.out.println("Dashboard URL: " + buildData.get("dashboard_url"));
				System.out.println("Public URL: " + buildData.get("public_url"));

			} else {
				System.out.println("Session details are null!");
			}
		} catch (Exception e) {
			System.err.println("Error fetching session details:");
			e.printStackTrace();
		}
	}

	private JSONObject getSessionDetails(String url, String info) throws Exception {
		String baseUrl = "https://api.lambdatest.com/automation/api/v1/";
		String apiUrl =  baseUrl + url + info;
		System.out.println("Connecting to URL: " + apiUrl);

		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			HttpGet request = new HttpGet(apiUrl);
			String authHeader = getBasicAuthHeader();
			request.addHeader("Authorization", authHeader);
			HttpResponse response = httpClient.execute(request);
			System.out.println("HTTP Response: " + response.getStatusLine());
			if (response.getStatusLine().getStatusCode() == 200) {
				String responseBody = EntityUtils.toString(response.getEntity());
				//System.out.println("Raw API Response: " + responseBody);
				JSONParser parser = new JSONParser();
				JSONObject jsonObject = (JSONObject) parser.parse(responseBody);
				System.out.println(jsonObject);
				return jsonObject;
			} else {
				System.out.println("Failed to fetch session details. HTTP Status: " + response.getStatusLine().getStatusCode());
				return null;
			}
		} catch (Exception e) {
			System.err.println("Exception occurred while making the HTTP request:");
			e.printStackTrace();
			throw e;
		}
	}

	private String getBasicAuthHeader() {
		String credentials = System.getenv("LT_USERNAME") + ":" + System.getenv("LT_ACCESS_KEY");
		if (credentials == null || credentials.isEmpty()) {
			throw new IllegalStateException("Environment variables LT_USERNAME and LT_ACCESS_KEY are not set.");
		}
		return "Basic " + java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
	}

	@BeforeMethod(alwaysRun = true)
	@Parameters(value = {"config", "environment"})
	public void setUp(String config_file, String environment) throws Exception {
		JSONParser parser = new JSONParser();
		JSONObject config = (JSONObject) parser.parse(new FileReader("src/test/resources/conf/" + config_file));
		JSONObject envs = (JSONObject) config.get("environments");

		DesiredCapabilities capabilities = new DesiredCapabilities();

		Map<String, String> envCapabilities = (Map<String, String>) envs.get(environment);
		Iterator it = envCapabilities.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry) it.next();
			capabilities.setCapability(pair.getKey().toString(), pair.getValue().toString());
		}

		Map<String, String> commonCapabilities = (Map<String, String>) config.get("capabilities");
		it = commonCapabilities.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry) it.next();
			if (capabilities.getCapability(pair.getKey().toString()) == null) {
				capabilities.setCapability(pair.getKey().toString(),
						(pair.getValue().toString().equalsIgnoreCase("true")
								|| pair.getValue().toString().equalsIgnoreCase("false")
								? Boolean.parseBoolean(pair.getValue().toString())
								: pair.getValue().toString()));
			}
		}
		capabilities.setCapability("name", this.getClass().getName());

		username = System.getenv("LT_USERNAME");
		if (username == null) {
			username = "anubhavb";
		}

		accessKey = System.getenv("LT_ACCESS_KEY");
		if (accessKey == null) {
			accessKey = "jfIpDzBoFpX3ZMxwDCOsz1b8qlvo8npozRHsLqpeifQoOwiLDK";
		}

		driver = new RemoteWebDriver(
				new URL("https://" + username + ":" + accessKey + "@" + config.get("server") + "/wd/hub"), capabilities);
		driver.manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);
		sessionId = driver.getSessionId().toString();
		WebDriverRunner.setWebDriver(driver);
	}

	@AfterMethod(alwaysRun = true)
	public void tearDown() {
		try {
			driver.executeScript("lambda-status=" + status);
			fetchSessionDetails(sessionId);
		} catch (Exception e) {
			System.err.println("Error updating test status on LambdaTest:");
			e.printStackTrace();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}
}
