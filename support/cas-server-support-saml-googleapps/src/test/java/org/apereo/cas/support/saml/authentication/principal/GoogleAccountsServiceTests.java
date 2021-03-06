package org.apereo.cas.support.saml.authentication.principal;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apereo.cas.authentication.CoreAuthenticationTestUtils;
import org.apereo.cas.authentication.principal.DefaultResponse;
import org.apereo.cas.authentication.principal.Response;
import org.apereo.cas.authentication.principal.ResponseBuilder;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.authentication.principal.ServiceFactory;
import org.apereo.cas.config.support.EnvironmentConversionServiceInitializer;
import org.apereo.cas.services.DefaultRegisteredServiceUsernameProvider;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.support.saml.AbstractOpenSamlTests;
import org.apereo.cas.support.saml.SamlProtocolConstants;
import org.apereo.cas.support.saml.config.SamlGoogleAppsConfiguration;
import org.apereo.cas.util.CompressionUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Scott Battaglia
 * @since 3.1
 */
@RunWith(SpringRunner.class)
@Import(SamlGoogleAppsConfiguration.class)
@TestPropertySource(locations = "classpath:/gapps.properties")
@ContextConfiguration(initializers = EnvironmentConversionServiceInitializer.class)
@Slf4j
public class GoogleAccountsServiceTests extends AbstractOpenSamlTests {

    private static final File FILE = new File(FileUtils.getTempDirectoryPath(), "service.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    @Qualifier("googleAccountsServiceFactory")
    private ServiceFactory factory;

    @Autowired
    @Qualifier("googleAccountsServiceResponseBuilder")
    private ResponseBuilder<GoogleAccountsService> googleAccountsServiceResponseBuilder;

    private GoogleAccountsService googleAccountsService;

    public GoogleAccountsService getGoogleAccountsService() {
        final MockHttpServletRequest request = new MockHttpServletRequest();

        final String samlRequest = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<samlp:AuthnRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
            + "ID=\"5545454455\" Version=\"2.0\" IssueInstant=\"Value\" "
            + "ProtocolBinding=\"urn:oasis:names.tc:SAML:2.0:bindings:HTTP-Redirect\" "
            + "ProviderName=\"https://localhost:8443/myRutgers\" AssertionConsumerServiceURL=\"https://localhost:8443/myRutgers\"/>";
        request.setParameter(SamlProtocolConstants.PARAMETER_SAML_REQUEST, encodeMessage(samlRequest));
        request.setParameter(SamlProtocolConstants.PARAMETER_SAML_RELAY_STATE, "RelayStateAddedHere");

        final RegisteredService regSvc = mock(RegisteredService.class);
        when(regSvc.getUsernameAttributeProvider()).thenReturn(new DefaultRegisteredServiceUsernameProvider());

        final ServicesManager servicesManager = mock(ServicesManager.class);
        when(servicesManager.findServiceBy(any(Service.class))).thenReturn(regSvc);

        return (GoogleAccountsService) factory.createService(request);
    }

    @Before
    public void setUp() {
        this.googleAccountsService = getGoogleAccountsService();
    }

    @Test
    public void verifyResponse() {
        final Response resp = googleAccountsServiceResponseBuilder.build(googleAccountsService, "SAMPLE_TICKET",
            CoreAuthenticationTestUtils.getAuthentication());
        assertEquals(DefaultResponse.ResponseType.POST, resp.getResponseType());
        final String response = resp.getAttributes().get(SamlProtocolConstants.PARAMETER_SAML_RESPONSE);
        assertNotNull(response);
        assertTrue(response.contains("NotOnOrAfter"));

        final Pattern pattern = Pattern.compile("NotOnOrAfter\\s*=\\s*\"(.+Z)\"");
        final Matcher matcher = pattern.matcher(response);
        final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        while (matcher.find()) {
            final String onOrAfter = matcher.group(1);
            final ZonedDateTime dt = ZonedDateTime.parse(onOrAfter);
            assertTrue(dt.isAfter(now));
        }
        assertTrue(resp.getAttributes().containsKey(SamlProtocolConstants.PARAMETER_SAML_RELAY_STATE));

    }

    private static String encodeMessage(final String xmlString) {
        return CompressionUtils.deflate(xmlString);
    }

    @Test
    public void serializeGoogleAccountService() throws Exception {
        final GoogleAccountsService service = getGoogleAccountsService();
        MAPPER.writeValue(FILE, service);
        final GoogleAccountsService service2 = MAPPER.readValue(FILE, GoogleAccountsService.class);
        assertEquals(service, service2);
    }
}
