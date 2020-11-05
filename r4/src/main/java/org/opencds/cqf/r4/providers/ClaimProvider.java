package org.opencds.cqf.r4.providers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.ClaimResponse.ErrorComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opencds.cqf.common.config.HapiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.api.model.DaoMethodOutcome;
import ca.uhn.fhir.jpa.rp.r4.ClaimResourceProvider;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.TokenParam;

public class ClaimProvider extends ClaimResourceProvider {
	private static final Logger logger = LoggerFactory.getLogger(ClaimProvider.class);
	IFhirResourceDao<Bundle> bundleDao;
	IFhirResourceDao<ClaimResponse> ClaimResponseDao;
	IFhirResourceDao<Patient> patientDao;
	DaoRegistry registry;
	JSONArray errors = new JSONArray();
	IParser parser = FhirContext.forR4().newJsonParser();
	boolean send275 = false;

	public ClaimProvider(ApplicationContext appCtx) {
		this.bundleDao = (IFhirResourceDao<Bundle>) appCtx.getBean("myBundleDaoR4", IFhirResourceDao.class);
		this.ClaimResponseDao = (IFhirResourceDao<ClaimResponse>) appCtx.getBean("myClaimResponseDaoR4",
				IFhirResourceDao.class);
		this.patientDao = (IFhirResourceDao<Patient>) appCtx.getBean("myPatientDaoR4", IFhirResourceDao.class);
		this.registry = appCtx.getBean(DaoRegistry.class);
		System.out.println("----\n---");
		System.out.println(this.ClaimResponseDao);
		System.out.println(this.bundleDao);
	}

	@Override
	public Class<Claim> getResourceType() {
		return Claim.class;
	}

	public String getSaltString() {
		String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
		StringBuilder salt = new StringBuilder();
		Random rnd = new Random();
		while (salt.length() < 16) { // length of the random string.
			int index = (int) (rnd.nextFloat() * SALTCHARS.length());
			salt.append(SALTCHARS.charAt(index));
		}
		String saltStr = salt.toString();
		return saltStr;
	}

	private JSONObject postHttpRequest(String Url, byte[] requestData, String apiKey) {
		JSONObject httpResponse = new JSONObject();
		BufferedReader in;
		try {
			logger.info("Posting to: " + Url);
			StringBuilder sb = new StringBuilder();
			URL url = new URL(Url);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			if (apiKey != null) {
				conn.setRequestProperty("x-api-key", apiKey);
			}
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "text/plain");
			conn.setRequestProperty("Accept", "text/plain");
			conn.setDoOutput(true);
			conn.getOutputStream().write(requestData);
			if (conn.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST) {
				in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));

			} else {
				in = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));

			}
			String line = null;
			while ((line = in.readLine()) != null) {
				sb.append(line);
			}
			httpResponse.put("statusCode", conn.getResponseCode());
			httpResponse.put("body", sb.toString());
			conn.disconnect();
			logger.info("StatusCode: " + conn.getResponseCode());
			logger.info("body: " + sb.toString());
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return httpResponse;
	}

	private JSONObject submitX12(String x12_generated, List<ErrorComponent> errorList, String strUrl) {
		JSONObject result = new JSONObject();
		try {
			byte[] postDataBytes = x12_generated.getBytes("UTF-8");
			result = postHttpRequest(strUrl, postDataBytes, null);

		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return result;
	}

	private JSONObject generateX12(String jsonStr, List<ErrorComponent> errorList, String strUrl, String apiKey) {
		JSONObject x12_generated = new JSONObject();
		try {
			// set envelpe to true to enclose generated x12 in soap envelope
			JSONObject x12request = new JSONObject();
			x12request.put("claimJson", jsonStr);
			x12request.put("envelope", true);
			System.out.println("Attaching 275 data: "+this.send275);
			x12request.put("send275",this.send275);
			String x12Str = x12request.toString();
			byte[] postDataBytes = x12Str.getBytes("UTF-8");
			x12_generated = postHttpRequest(strUrl, postDataBytes, apiKey);
			if (x12_generated.has("statusCode")) {
				if ((int) x12_generated.get("statusCode") >= HttpURLConnection.HTTP_BAD_REQUEST) {
					JSONObject response = new JSONObject(x12_generated.getString("body"));
					if (response.has("errors")){
						JSONArray errors = response.getJSONArray("errors");
						for (int i = 0; i < errors.length(); i++) {
							JSONObject errorObj = new JSONObject(errors.get(i).toString());
							System.out.println("errorObj: " + errorObj);
							String code = errorObj.getString("code");
							String message = errorObj.getString("message");
							errorList.add(generateErrorComponent(code, message));
						}
					} else {
						errorList.add(generateErrorComponent(x12_generated.get("statusCode").toString(), response.toString()));
					} 
					
				}
			}
			// System.out.println("\n x12_generated :" + x12_generated);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return x12_generated;
	}

	private ClaimResponse readX12Response(String x12Str, List<ErrorComponent> errorList, String strUrl, String apiKey) {
		ClaimResponse response = new ClaimResponse();
		try {
			byte[] postDataBytes = x12Str.getBytes("UTF-8");
			JSONObject httpResponse = postHttpRequest(strUrl, postDataBytes, apiKey);
			if (httpResponse.has("statusCode")) {
				if ((int) httpResponse.get("statusCode") < HttpURLConnection.HTTP_BAD_REQUEST) {
					IParser parser = FhirContext.forR4().newJsonParser();
					response = parser.parseResource(ClaimResponse.class, httpResponse.getString("body"));
					return response;
				} else {
					response.setStatus(ClaimResponse.ClaimResponseStatus.ENTEREDINERROR);
					response.setOutcome(ClaimResponse.RemittanceOutcome.ERROR);
					errorList.add(generateErrorComponent(String.valueOf((int) httpResponse.get("statusCode")),
							httpResponse.getString("body")));
					response.setError(errorList);

				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		response.setStatus(ClaimResponse.ClaimResponseStatus.ENTEREDINERROR);
		response.setOutcome(ClaimResponse.RemittanceOutcome.ERROR);
		errorList.add(generateErrorComponent("500", "Error encountered when connecting to X12 Reader"));
		response.setError(errorList);
		return response;
	}

	private ClaimResponse generateClaimResponse(Patient patient, Claim claim) {
		ClaimResponse retVal = new ClaimResponse();
		try {

			if (claim != null) {
				if (claim.getIdentifier().size() > 0) {
					String claimIdentifierValue = claim.getIdentifier().get(0).getValue();
					String claimIdentifierSystem = claim.getIdentifier().get(0).getSystem();
					Reference reqRef = new Reference();
					if (!claimIdentifierValue.isEmpty()) {
						Identifier claimIdentifierObj = new Identifier();
						claimIdentifierObj.setValue(claimIdentifierValue);
						claimIdentifierObj.setSystem(claimIdentifierSystem);
						reqRef.setIdentifier(claimIdentifierObj);
					}
					retVal.setRequest(reqRef);
				}
				retVal.setStatus(ClaimResponse.ClaimResponseStatus.ACTIVE);
				retVal.setOutcome(ClaimResponse.RemittanceOutcome.QUEUED);
				retVal.setUse(ClaimResponse.Use.PREAUTHORIZATION);
				retVal.setType(claim.getType());
				if (claim.getSubType() != null) {
					retVal.setSubType(claim.getSubType());
				}

			}

			retVal.setCreated(new Date());
			retVal.setUse(ClaimResponse.Use.PREAUTHORIZATION);
			Identifier claimResIdentifier = new Identifier();
			claimResIdentifier.setSystem("http://identifiers.mettles.com");
			String ref = getSaltString();
			claimResIdentifier.setValue(ref);
			retVal.addIdentifier(claimResIdentifier);

		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return retVal;
	}

	private String getPayerURL(Bundle bundle, Claim claim, List<ErrorComponent> errorList, String searchURL) {
		String payerURL = null;
		String insurerID = claim.getInsurer().getReference().replace("Organization/", "");
		System.out.println("INS: " + insurerID);
		System.out.println(bundle.getEntry().size());
		for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
			if (entry.getResource().getIdElement().getIdPart().equals(insurerID)) {
				Organization insurer = (Organization) entry.getResource();
				String paramValue = "";
				for (Identifier identifier :insurer.getIdentifier()){
					if (paramValue != ""){
						paramValue += ",";
					}
					paramValue += identifier.getSystem()+"|"+identifier.getValue();
				}
				JSONObject params = new JSONObject();
				System.out.println("Insurer identifiers: " + paramValue);
				try {
					params.put("payer_name", paramValue);
					byte[] postDataBytes = null;
					postDataBytes = params.toString().getBytes("UTF-8");
					JSONObject httpResponse = postHttpRequest(searchURL, postDataBytes, null);
					if (httpResponse.has("statusCode")) {
						if ((int) httpResponse.get("statusCode") < HttpURLConnection.HTTP_BAD_REQUEST) {
							String payerEndPoint = getEndPoint(httpResponse.getString("body"));
							if (payerEndPoint != null) {
								return payerEndPoint;
							} else {
								errorList.add(generateErrorComponent("400",
										"Payer Endpoint for X12 Submission not available"));
							}
						} else {
							errorList.add(generateErrorComponent("400", httpResponse.getString("body")));

						}
					} else {
						errorList.add(generateErrorComponent("400", "Error when connecting to Payer Search URL"));
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}
		return payerURL;
	}

	private String getEndPoint(String object) {
		String payerURL = null;
		try {
			JSONObject searchResObj = new JSONObject(object);
			if (searchResObj.has("entry")) {
				JSONArray entries = new JSONArray(searchResObj.get("entry").toString());
				for (int i = 0; i < entries.length(); i++) {
					JSONObject resource = entries.getJSONObject(i);
					if (resource.getString("resourceType").equals("Endpoint")) {
						Endpoint endpoint = parser.parseResource(Endpoint.class, resource.toString());
						if (endpoint.getName().equals("X12 EndPoint")) {
							payerURL = endpoint.getAddress();
						}
						// If x12 275 endpoint exists send claim data along with x12
						if (endpoint.getName().equals("X12 275 EndPoint")) {
							System.out.println("Found x12 275 endpoints");
							this.send275=true;
						}
					}
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return payerURL;
	}

	private Patient getPatient(Patient patient) {
		Patient serverPatient = null;
		for (Identifier identifier : patient.getIdentifier()) {
			SearchParameterMap map = new SearchParameterMap();
			TokenParam param = new TokenParam();
			param.setSystem(identifier.getSystem());
			param.setValue(identifier.getValue());
			map.add("identifier", param);
			System.out.println(" search : " + map.toNormalizedQueryString(FhirContext.forR4()));
			IBundleProvider patientProvider = registry.getResourceDao("Patient").search(map);
			List<IBaseResource> patientList = patientProvider.getResources(0, patientProvider.size());
			System.out.println("patient available:" + patientList.size());
			if (patientList.size() > 0) {
				serverPatient = (Patient) patientList.get(0);
				return serverPatient;
			}
		}

		return serverPatient;
	}

	// @Create
	@Operation(name = "$submit", idempotent = true)
	public Resource claimSubmit(RequestDetails details,
			@OperationParam(name = "claim", min = 1, max = 1, type = Bundle.class) Bundle bundle)
			throws RuntimeException {
		System.out.println("\n\n SUBMIT CLAIM \n\n");
		Patient patient = null;
		Patient serverPatient = null;
		Claim claim = null;
		Bundle responseBundle = new Bundle();
		Bundle claimBundle = new Bundle();
		ClaimResponse claimResponse = null;
		List<ErrorComponent> errorList = new ArrayList<ErrorComponent>();
		try {
			claimBundle.setType(bundle.getType());
			claimBundle.setIdentifier(bundle.getIdentifier());
			for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
				if (!entry.getResource().getResourceType().toString().equals("DocumentReference")) {
					claimBundle.addEntry(entry);
				}
				if (entry.getResource().getResourceType().toString().equals("Claim")) {
					try {
						claim = (Claim) entry.getResource();

					} catch (Exception e) {
						e.printStackTrace();
					}
				} else if (entry.getResource().getResourceType().toString().equals("Patient")) {
					try {
						patient = (Patient) entry.getResource();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			String payerSearchUrl = HapiProperties.getProperty("payer_search_url");
			if (payerSearchUrl == null) {
				throw new RuntimeException("Payer search Url not available");
			}
			String x12GenerateUrl = HapiProperties.getProperty("x12_generator_url");
			if (x12GenerateUrl == null) {
				throw new RuntimeException("X12 Generate url not available");
			}
			String apiKey = HapiProperties.getProperty("x12_generator_key");
			if (apiKey == null) {
				throw new RuntimeException("API key for X12 Generate url is not available");
			}
			String x12ReaderUrl = HapiProperties.getProperty("x12_reader_url");
			if (x12ReaderUrl == null) {
				throw new RuntimeException("X12 Reader url not available");
			}
			String readerApiKey = HapiProperties.getProperty("x12_reader_key");
			if (readerApiKey == null) {
				throw new RuntimeException("API key for X12 Reader url is not available");
			}
			// Generate a ClaimResponse with data from Bundle to return the response
			ClaimResponse retVal = generateClaimResponse(patient, claim);
			// Get the payer url to submit the generated x12
			String x12SubmitUrl = getPayerURL(bundle, claim, errorList, payerSearchUrl);
			System.out.println("x12SubmitUrl " + x12SubmitUrl);
			if (x12SubmitUrl != null) {
				IParser jsonParser = details.getFhirContext().newJsonParser();
				String jsonStr = jsonParser.encodeResourceToString(claimBundle);
				JSONObject x12_generated = generateX12(jsonStr, errorList, x12GenerateUrl, apiKey);
				if (x12_generated.has("statusCode")
						&& (int) x12_generated.get("statusCode") < HttpURLConnection.HTTP_BAD_REQUEST) {
					// Submit the generated X12 to the payer url
					JSONObject x12_submitResponse = submitX12((String) x12_generated.get("body"), errorList,
							x12SubmitUrl);
					if (x12_submitResponse.has("statusCode")) {
						if ((int) x12_submitResponse.get("statusCode") >= HttpURLConnection.HTTP_BAD_REQUEST) {
							errorList.add(this.generateErrorComponent(
									String.valueOf((int) x12_submitResponse.get("statusCode")),
									x12_submitResponse.getString("body")));

						} else {
							// Send the response received from X12 submission for getting relevant data
							ClaimResponse recdClaimResponse = readX12Response(x12_submitResponse.getString("body"),
									errorList, x12ReaderUrl, readerApiKey);
							if (recdClaimResponse != null) {
								ClaimResponse updatedResponse = updateClaimResponse(retVal, recdClaimResponse);
								// Need to add the identifier received to this claimresponse
								if (recdClaimResponse.getIdentifier().size() > 0) {
									// Adding the first identifier recieved
									retVal.addIdentifier(recdClaimResponse.getIdentifier().get(0));
								}
								// Before adding claimResponse check if patient already available in the server
								if (patient != null) {
									serverPatient = getPatient(patient);
								}
								if (serverPatient == null) {
									
									patient.setId(new IdType());
									patient.setGeneralPractitioner(null);
									System.out.println("creating patient \n"+FhirContext.forR4().newJsonParser().encodeResourceToString(patient));
									DaoMethodOutcome patientOutcome = patientDao.create(patient);
									serverPatient = (Patient) patientOutcome.getResource();

								}
								updatedResponse.setPatient(new Reference(serverPatient.getId()));
								DaoMethodOutcome claimResponseOutcome = ClaimResponseDao.create(updatedResponse);
								claimResponse = (ClaimResponse) claimResponseOutcome.getResource();
								responseBundle.addEntry(new Bundle.BundleEntryComponent().setResource(claimResponse));
								responseBundle.addEntry(new Bundle.BundleEntryComponent().setResource(serverPatient));
								return responseBundle;

							}

						}

					}

				}

			}
			if (errorList.size() > 0) {
				retVal.setStatus(ClaimResponse.ClaimResponseStatus.ENTEREDINERROR);
				retVal.setOutcome(ClaimResponse.RemittanceOutcome.ERROR);
				System.out.println("Erros size: " + errorList.size());
				retVal.setError(errorList);
				System.out.println(retVal.getError().size());

			}

			if (claimResponse == null) {
				responseBundle.addEntry(new Bundle.BundleEntryComponent().setResource(retVal));

			}

			return responseBundle;

		} catch (RuntimeException re) {
			throw re;
		} catch (Exception ex) {
			ex.printStackTrace();
			throw new RuntimeException(ex.getLocalizedMessage());
		}
	}

	private ErrorComponent generateErrorComponent(String code, String message) {
		ErrorComponent errorComponent = new ErrorComponent();
		CodeableConcept errorCode = new CodeableConcept();
		errorCode.addCoding(new Coding().setDisplay(message).setCode(code));
		errorComponent.setCode(errorCode);
		return errorComponent;
	}

	private ClaimResponse updateClaimResponse(ClaimResponse currentResponse, ClaimResponse latestResponse) {
		currentResponse.setError(latestResponse.getError());
		currentResponse.setOutcome(latestResponse.getOutcome());
		currentResponse.setStatus(latestResponse.getStatus());
		if (latestResponse.getPreAuthRef() != null) {
			currentResponse.setPreAuthRef(latestResponse.getPreAuthRef());
		}
		if (latestResponse.getExtension().size() > 0) {
			currentResponse.setExtension(latestResponse.getExtension());
		}
		if (latestResponse.getProcessNote().size() > 0) {
			currentResponse.setProcessNote(latestResponse.getProcessNote());
		}

		return currentResponse;
	}

	@Operation(name = "$build", idempotent = true)
	public Bundle build(RequestDetails theRequestDetails,
			@OperationParam(name = "bundle", min = 1, max = 1, type = Bundle.class) Bundle bundle) {
		// System.out.println("\n\n op call ssssss"+this.getDao());

		return bundle;

	}

	@Operation(name = "$update-claim", idempotent = true)
	public Bundle updateResponse(RequestDetails details,
			@OperationParam(name = "response", min = 1, max = 1, type = ClaimResponse.class) ClaimResponse claimResponse)
			throws RuntimeException {
		Boolean claimFound = false;
		Bundle responseBundle = new Bundle();
		ClaimResponse fhirClaimResponse = null;
		List<Identifier> claimIdentifiers = claimResponse.getIdentifier();
		for (Identifier identifier : claimIdentifiers) {
			SearchParameterMap identifierSearch = new SearchParameterMap();
			TokenParam param = new TokenParam();
			param.setSystem(identifier.getSystem());
			param.setValue(identifier.getValue());
			identifierSearch.add("identifier", param);
			System.out.println(" search : " + identifierSearch.toNormalizedQueryString(FhirContext.forR4()));
			IBundleProvider bundleProvider = this.ClaimResponseDao.search(identifierSearch);
			List<IBaseResource> resources = bundleProvider.getResources(0, bundleProvider.size());
			for (Iterator iterator = resources.iterator(); iterator.hasNext();) {
				fhirClaimResponse = (ClaimResponse) (iterator.next());
				System.out.println("found: " + fhirClaimResponse.toString());
				claimFound = true;
				break;
			}
		}
		if (!claimFound) {
			System.out.println("Claim Response sent doesn't match with any available ClaimResponse");

		} else {

			ClaimResponse updatedResponse = updateClaimResponse(fhirClaimResponse, claimResponse);
			ClaimResponseDao.update(updatedResponse);

		}

		return responseBundle;

	}

}
