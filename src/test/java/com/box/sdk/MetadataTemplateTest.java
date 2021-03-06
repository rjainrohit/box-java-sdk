package com.box.sdk;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.junit.Assert.assertEquals;

/**
 * {@link MetadataTemplate} related unit tests.
 */
public class MetadataTemplateTest {

    @ClassRule
    public static final WireMockClassRule WIRE_MOCK_CLASS_RULE = new WireMockClassRule(53621);
    private BoxAPIConnection api = TestConfig.getAPIConnection();

    @Test
    @Category(IntegrationTest.class)
    public void testDeleteMetadataTemplateSucceeds() {
        String scope = "enterprise";
        String template = "testtemplate";
        String displayName = "Test Template";
        Boolean templateIsHidden = false;
        int errorResponseStatusCode = 404;

        BoxAPIConnection api = new BoxAPIConnection(TestConfig.getAccessToken());

        try {
            MetadataTemplate.createMetadataTemplate(api, scope, template, displayName, templateIsHidden, null);
        } catch (BoxAPIException e) {
            System.out.print("Error while making callout to createMetdataTemplate(): " + e);
        }

        MetadataTemplate.deleteMetadataTemplate(api, scope, template);

        try {
            MetadataTemplate.getMetadataTemplate(api, template);
        } catch (BoxAPIException e) {
            Assert.assertEquals(errorResponseStatusCode, e.getResponseCode());
        }
    }

    @Test
    @Category(IntegrationTest.class)
    public void createMetadataTemplateSucceeds() {
        BoxAPIConnection api = new BoxAPIConnection(TestConfig.getAccessToken());

        MetadataTemplate.Field ctField = new MetadataTemplate.Field();
        ctField.setType("string");
        ctField.setKey("customerTeam");
        ctField.setDisplayName("Customer Team");

        MetadataTemplate.Field fyField = new MetadataTemplate.Field();
        fyField.setType("enum");
        fyField.setKey("fy");
        fyField.setDisplayName("FY");

        List<String> options = new ArrayList<String>();
        options.add("FY16");
        options.add("FY17");
        fyField.setOptions(options);

        List<MetadataTemplate.Field> fields = new ArrayList<MetadataTemplate.Field>();
        fields.add(ctField);
        fields.add(fyField);

        try {
            MetadataTemplate template = MetadataTemplate.createMetadataTemplate(api, "enterprise",
                "documentFlow03", "Document Flow 03", false, fields);
        } catch (BoxAPIException apiEx) {
            //Delete MetadataTemplate is yet to be supported. Due to that template might be existing already.
            //This expects the conflict error. To check the MetadataTemplate creation, please replace the id.
            Assert.assertEquals(409, apiEx.getResponseCode());
            Assert.assertTrue(apiEx.getResponse().contains("Template key already exists in this scope"));
        }

        MetadataTemplate storedTemplate = MetadataTemplate.getMetadataTemplate(api, "documentFlow03");
        Assert.assertNotNull(storedTemplate);
    }

    private List<MetadataTemplate.FieldOperation> addFieldsHelper() {
        List<MetadataTemplate.FieldOperation> fieldOperations = new ArrayList<MetadataTemplate.FieldOperation>();
        MetadataTemplate.FieldOperation customerFieldOp = new MetadataTemplate.FieldOperation();
        customerFieldOp.setOp(MetadataTemplate.Operation.addField);

        MetadataTemplate.Field customerTeam = new MetadataTemplate.Field();
        customerTeam.setType("string");
        customerTeam.setKey("customerTeam");
        customerTeam.setDisplayName("Customer Team");
        customerFieldOp.setData(customerTeam);
        fieldOperations.add(customerFieldOp);

        MetadataTemplate.FieldOperation departmentFieldOp = new MetadataTemplate.FieldOperation();
        departmentFieldOp.setOp(MetadataTemplate.Operation.addField);

        MetadataTemplate.Field deptField = new MetadataTemplate.Field();
        deptField.setType("enum");
        deptField.setKey("department");
        deptField.setDisplayName("Department");

        List<String> options = new ArrayList<String>();
        options.add("Beauty");
        options.add("Shoes");
        deptField.setOptions(options);
        departmentFieldOp.setData(deptField);

        fieldOperations.add(departmentFieldOp);
        return fieldOperations;
    }

    @Test
    @Category(IntegrationTest.class)
    public void updateMetadataTemplateFieldsSucceeds() {
        BoxAPIConnection api = new BoxAPIConnection(TestConfig.getAccessToken());

        try {
            //Test adding fields
            List<MetadataTemplate.FieldOperation> fieldOperations = this.addFieldsHelper();
            MetadataTemplate template = MetadataTemplate.updateMetadataTemplate(api,
                "enterprise", "documentFlow03", fieldOperations);
            Assert.assertNotNull(template);

            boolean foundDeptField = false;
            boolean foundCustField = false;
            for (MetadataTemplate.Field field : template.getFields()) {
                if ("department".equals(field.getKey())) {
                    Assert.assertEquals("enum", field.getType());
                    Assert.assertEquals("Department", field.getDisplayName());
                    Assert.assertEquals(2, field.getOptions().size());
                    foundDeptField = true;
                } else if ("customerTeam".equals(field.getKey())) {
                    foundCustField = true;
                }
            }
            Assert.assertEquals("department field was not found", true, foundDeptField);
            Assert.assertEquals("customer field was not found", true, foundCustField);

            //Test editing fields
            fieldOperations.clear();

            MetadataTemplate.FieldOperation customerFieldOp = new MetadataTemplate.FieldOperation();
            customerFieldOp.setOp(MetadataTemplate.Operation.editField);
            customerFieldOp.setFieldKey("customerTeam");

            MetadataTemplate.Field customerTeam = new MetadataTemplate.Field();
            customerTeam.setDisplayName("Customer Team Renamed");
            customerTeam.setKey("newCustomerTeamKey");
            customerFieldOp.setData(customerTeam);
            fieldOperations.add(customerFieldOp);

            MetadataTemplate.FieldOperation editEnumOption = new MetadataTemplate.FieldOperation();
            editEnumOption.setOp(MetadataTemplate.Operation.editEnumOption);
            editEnumOption.setFieldKey("department");
            editEnumOption.setEnumOptionKey("Shoes");


            MetadataTemplate.Field deptField = new MetadataTemplate.Field();
            deptField.setKey("Baby");
            editEnumOption.setData(deptField);

            fieldOperations.add(editEnumOption);

            template = MetadataTemplate.updateMetadataTemplate(api,
                "enterprise", "documentFlow03", fieldOperations);
            boolean foundBabyEnumOption = false;
            for (MetadataTemplate.Field field : template.getFields()) {
                if ("customerTeam".equals(field.getKey())) {
                    Assert.fail("'customerTeam' field key should have been changed to 'newCustomerTeamKey'");
                } else if ("department".equals(field.getKey())) {
                    for (String option : field.getOptions()) {
                        if ("Baby".equals(option)) {
                            foundBabyEnumOption = true;
                        }
                    }
                } else if ("newCustomerTeamKey".equals(field.getKey())) {
                    Assert.assertEquals("Display name should have been updated",
                        "Customer Team Renamed", field.getDisplayName());
                }
            }
            Assert.assertEquals("Baby enum option was not found", true, foundBabyEnumOption);

            //Test removing fields
            fieldOperations.clear();

            MetadataTemplate.FieldOperation deleteDeptField = new MetadataTemplate.FieldOperation();
            deleteDeptField.setOp(MetadataTemplate.Operation.removeField);
            deleteDeptField.setFieldKey("newCustomerTeamKey");
            fieldOperations.add(deleteDeptField);

            MetadataTemplate.FieldOperation deleteEnumOption = new MetadataTemplate.FieldOperation();
            deleteEnumOption.setOp(MetadataTemplate.Operation.removeEnumOption);
            deleteEnumOption.setFieldKey("department");
            deleteEnumOption.setEnumOptionKey("Baby");

            fieldOperations.add(deleteEnumOption);

            template = MetadataTemplate.updateMetadataTemplate(api, "enterprise", "documentFlow03", fieldOperations);

            for (MetadataTemplate.Field field : template.getFields()) {
                if ("newCustomerTeamKey".equals(field.getKey())) {
                    Assert.fail("newCustomerTeamKey field key should have been deleted");
                } else if ("department".equals(field.getKey())) {
                    for (String option : field.getOptions()) {
                        if ("Baby".equals(option)) {
                            Assert.fail("Baby enum option should have been deleted");
                        }
                    }
                }
            }
        } finally {
            this.tearDownFields(api);
        }
    }

    @Test
    @Category(IntegrationTest.class)
    public void getAllMetadataSucceeds() {
        BoxFile uploadedFile = null;
        BoxAPIConnection api = new BoxAPIConnection(TestConfig.getAccessToken());
        try {
            BoxFolder rootFolder = BoxFolder.getRootFolder(api);
            String fileName = "[getAllMetadataSucceeds] Test File.txt";
            byte[] fileBytes = "Non-empty string".getBytes(StandardCharsets.UTF_8);

            InputStream uploadStream = new ByteArrayInputStream(fileBytes);
            uploadedFile = rootFolder.uploadFile(uploadStream, fileName).getResource();

            uploadedFile.createMetadata(new Metadata().add("/firstName", "John").add("/lastName", "Smith"));
            Metadata check1 = uploadedFile.getMetadata();
            Assert.assertNotNull(check1);
            Assert.assertEquals("John", check1.getString("/firstName"));
            Assert.assertEquals("Smith", check1.getString("/lastName"));

            //Create fields before test
            List<MetadataTemplate.FieldOperation> fieldOperations = this.addFieldsHelper();
            MetadataTemplate template = MetadataTemplate.updateMetadataTemplate(api,
                "enterprise", "documentFlow03", fieldOperations);
            Assert.assertNotNull(template);

            Metadata customerMetaData = new Metadata();
            customerMetaData.add("/customerTeam", "MyTeam");
            customerMetaData.add("/department", "Beauty");

            uploadedFile.createMetadata("documentFlow03", "enterprise", customerMetaData);

            Iterable<Metadata> allMetadata = uploadedFile.getAllMetadata("/firstName", "/lastName");
            Assert.assertNotNull(allMetadata);
            Iterator<Metadata> iter = allMetadata.iterator();
            int numTemplates = 0;
            while (iter.hasNext()) {
                Metadata metadata = iter.next();
                numTemplates++;
                if (metadata.getTemplateName().equals("properties")) {
                    Assert.assertEquals("John", metadata.getString("/firstName"));
                    Assert.assertEquals("Smith", metadata.getString("/lastName"));
                }
                if (metadata.getTemplateName().equals("documentFlow03")) {
                    Assert.assertEquals("MyTeam", metadata.getString("/customerTeam"));
                    Assert.assertEquals("Beauty", metadata.getString("/department"));
                }
            }
            Assert.assertTrue("Should have at least 2 templates", numTemplates >= 2);
        } finally {
            if (uploadedFile != null) {
                uploadedFile.delete();
            } else {
                Assert.fail("File ");
            }
            this.tearDownFields(api);
        }
    }

    @Test
    @Category(UnitTest.class)
    public void testGetAllEnterpriseMetadataTemplatesSucceeds() throws IOException {
        String result = "";
        final String firstEntryID = "12345";
        final String firstTemplateKey = "Test Template";
        final String secondEntryID = "23131";
        final String secondTemplateKey = "Test Template 2";
        final String metadataTemplateURL = "/metadata_templates/enterprise";

        result = TestConfig.getFixture("BoxMetadataTemplate/GetAllEnterpriseTemplates200");

        WIRE_MOCK_CLASS_RULE.stubFor(WireMock.get(WireMock.urlPathEqualTo(metadataTemplateURL))
            .withQueryParam("limit", WireMock.containing("100"))
            .willReturn(WireMock.aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(result)));

        Iterator<MetadataTemplate> templates = MetadataTemplate.getEnterpriseMetadataTemplates(this.api).iterator();
        MetadataTemplate template = templates.next();

        Assert.assertEquals(firstEntryID, template.getID());
        Assert.assertEquals(firstTemplateKey, template.getTemplateKey());

        MetadataTemplate secondTemplate = templates.next();

        Assert.assertEquals(secondEntryID, secondTemplate.getID());
        Assert.assertEquals(secondTemplateKey, secondTemplate.getTemplateKey());
    }


    private void tearDownFields(BoxAPIConnection api) {
        List<MetadataTemplate.FieldOperation> fieldOperations = new ArrayList<MetadataTemplate.FieldOperation>();
        MetadataTemplate template = MetadataTemplate.getMetadataTemplate(api, "documentFlow03", "enterprise");
        for (MetadataTemplate.Field field : template.getFields()) {
            MetadataTemplate.FieldOperation deleteField = new MetadataTemplate.FieldOperation();
            deleteField.setOp(MetadataTemplate.Operation.removeField);
            deleteField.setFieldKey(field.getKey());
            fieldOperations.add(deleteField);
        }
        MetadataTemplate updatedTemplate = MetadataTemplate.updateMetadataTemplate(api,
            "enterprise", "documentFlow03", fieldOperations);
    }

    @Test
    @Category(UnitTest.class)
    public void testGetOptionsReturnsListOfStrings() throws IOException {
        String result = "";
        final String templateID = "f7a9891f";
        final String metadataTemplateURL = "/metadata_templates/" + templateID;
        final ArrayList<String> list = new ArrayList<String>() {
            {
                add("Beauty");
                add("Shoes");
            }
        };
        result = TestConfig.getFixture("BoxMetadataTemplate/GetMetadataTemplateOptionInfo200");
        WIRE_MOCK_CLASS_RULE.stubFor(WireMock.get(WireMock.urlPathEqualTo(metadataTemplateURL))
            .willReturn(WireMock.aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(result)));

        MetadataTemplate template = MetadataTemplate.getMetadataTemplateByID(this.api, templateID);
        List<MetadataTemplate.Field> fields = template.getFields();
        for (MetadataTemplate.Field field : fields) {
            if (field.getKey().equals("department")) {
                Assert.assertEquals(list, field.getOptions());
            }
        }
    }

    @Test
    @Category(UnitTest.class)
    public void testGetOptionsReturnsListOfOptionsObject() throws IOException {
        String result = "";
        final String templateID = "f7a9891f";
        final String metadataTemplateURL = "/metadata_templates/" + templateID;
        result = TestConfig.getFixture("BoxMetadataTemplate/GetMetadataTemplateOptionInfo200");

        WIRE_MOCK_CLASS_RULE.stubFor(WireMock.get(WireMock.urlPathEqualTo(metadataTemplateURL))
            .willReturn(WireMock.aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(result)));

        MetadataTemplate template = MetadataTemplate.getMetadataTemplateByID(this.api, templateID);
        List<MetadataTemplate.Field> fields = template.getFields();

        for (MetadataTemplate.Field field : fields) {
            if (field.getKey().equals("department")) {
                List<MetadataTemplate.Option> options = field.getOptionsObjects();
                MetadataTemplate.Option firstOption = options.get(0);
                MetadataTemplate.Option secondOption = options.get(1);
                Assert.assertEquals("Beauty", firstOption.getKey());
                Assert.assertEquals("f7a9895f", firstOption.getID());
                Assert.assertEquals("Shoes", secondOption.getKey());
                Assert.assertEquals("f7a9896f", secondOption.getID());
            }
        }
    }

    @Test
    @Category(UnitTest.class)
    public void testSetOptionReturnsCorrectly() throws IOException {
        String result = "";
        final String metadataTemplateURL = "/metadata_templates/schema";
        result = TestConfig.getFixture("BoxMetadataTemplate/CreateMetadataTemplate200");

        JsonObject keyObject = new JsonObject();
        keyObject.add("key", "FY16");

        JsonObject secondKeyObject = new JsonObject();
        secondKeyObject.add("key", "FY17");

        JsonArray optionsArray = new JsonArray();
        optionsArray.add(keyObject);
        optionsArray.add(secondKeyObject);

        JsonObject enumObject = new JsonObject();
        enumObject.add("type", "enum")
            .add("key", "fy")
            .add("displayName", "FY")
            .add("options", optionsArray);

        JsonArray fieldsArray = new JsonArray();
        fieldsArray.add(enumObject);

        JsonObject templateBody = new JsonObject();
        templateBody.add("scope", "enterprise")
            .add("displayName", "Document Flow 03")
            .add("hidden", false)
            .add("templateKey", "documentFlow03")
            .add("fields", fieldsArray);

        WIRE_MOCK_CLASS_RULE.stubFor(WireMock.post(WireMock.urlPathEqualTo(metadataTemplateURL))
            .withRequestBody(WireMock.equalToJson(templateBody.toString()))
            .willReturn(WireMock.aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(result)));

        MetadataTemplate.Field fyField = new MetadataTemplate.Field();
        fyField.setType("enum");
        fyField.setKey("fy");
        fyField.setDisplayName("FY");

        List<String> options = new ArrayList<String>();
        options.add("FY16");
        options.add("FY17");
        fyField.setOptions(options);

        List<MetadataTemplate.Field> fields = new ArrayList<MetadataTemplate.Field>();
        fields.add(fyField);

        MetadataTemplate template = MetadataTemplate.createMetadataTemplate(this.api, "enterprise",
            "documentFlow03", "Document Flow 03", false, fields);

        Assert.assertEquals("FY16", template.getFields().get(0).getOptions().get(0));
        Assert.assertEquals("FY17", template.getFields().get(0).getOptions().get(1));
    }

    @Test
    @Category(UnitTest.class)
    public void testUpdateMetadataReturnsCorrectly() throws IOException {
        String result = "";
        final String metadataTemplateURL = "/metadata_templates/enterprise/documentFlow03/schema";
        result = TestConfig.getFixture("BoxMetadataTemplate/UpdateMetadataTemplate200");

        String scope = "enterprise";
        String templateKey = "documentFlow03";

        JsonObject editCopyDataObject = new JsonObject();
        editCopyDataObject.add("copyInstanceOnItemCopy", true);

        JsonObject editCopyOperation = new JsonObject();
        editCopyOperation.add("op", "editTemplate");
        editCopyOperation.add("data", editCopyDataObject);

        JsonArray body = new JsonArray();
        body.add(editCopyOperation);

        WIRE_MOCK_CLASS_RULE.stubFor(WireMock.put(WireMock.urlPathEqualTo(metadataTemplateURL))
            .withRequestBody(WireMock.equalToJson(body.toString()))
            .willReturn(WireMock.aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(result)));

        List<MetadataTemplate.FieldOperation> fieldOperations = new ArrayList<MetadataTemplate.FieldOperation>();

        MetadataTemplate.FieldOperation editTemplate = new MetadataTemplate.FieldOperation();
        editTemplate.setOp(MetadataTemplate.Operation.editTemplate);
        MetadataTemplate.Field copyInstanceField = new MetadataTemplate.Field();
        copyInstanceField.setCopyInstanceOnItemCopy(Boolean.TRUE);
        editTemplate.setData(copyInstanceField);

        fieldOperations.add(editTemplate);

        MetadataTemplate template = MetadataTemplate.updateMetadataTemplate(this.api, "enterprise",
            "documentFlow03", fieldOperations);
        Assert.assertEquals("Copy instance on item copy not set", true, template.getCopyInstanceOnItemCopy());
    }

    @Test
    @Category(UnitTest.class)
    public void testExecuteMetadataQuery() throws IOException {
        final String metadataQueryURL = "/metadata_queries/execute_read";

        final String from = "enterprise_67890.relayWorkflowInformation";
        final String query = "templateName >= :arg";
        final JsonObject queryParameters = new JsonObject().add("arg", "Templ Name");
        final String ancestorFolderId = "0";
        final String indexName = null;
        final JsonArray orderBy = null;
        final int limit = 2;
        final String marker = null;

        // First request will return a page of results with two items
        String request1 = TestConfig.getFixture("BoxMetadataTemplate/MetadataQuery1stRequest");
        String result1 = TestConfig.getFixture("BoxMetadataTemplate/MetadataQuery1stResponse200");
        WIRE_MOCK_CLASS_RULE.stubFor(post(urlPathEqualTo(metadataQueryURL))
            .withRequestBody(equalToJson(request1))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(result1)));

        // Second request will contain a marker and will return a page of results with remaining one item
        String result2 = TestConfig.getFixture("BoxMetadataTemplate/MetadataQuery2ndResponse200");
        String request2 = TestConfig.getFixture("BoxMetadataTemplate/MetadataQuery2ndRequest");
        WIRE_MOCK_CLASS_RULE.stubFor(post(urlPathEqualTo(metadataQueryURL))
            .withRequestBody(equalToJson(request2))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(result2)));

        // Make the first request and get the result
        BoxResourceIterable<BoxItem.Info> results = MetadataTemplate.executeMetadataQuery(this.api,
            from, query, queryParameters, ancestorFolderId, indexName, orderBy, limit, marker);

        // First item on the first page of results
        BoxFile.Info currBoxItem = (BoxFile.Info) results.iterator().next();
        assertEquals("file", currBoxItem.getType());
        assertEquals("123450", currBoxItem.getID());
        assertEquals("1.jpg", currBoxItem.getName());
        Metadata metadata = currBoxItem.getMetadata("relayWorkflowInformation", "enterprise_67890");
        Assert.assertEquals("relayWorkflowInformation", metadata.getTemplateName());
        Assert.assertEquals("enterprise_67890", metadata.getScope());
        Assert.assertEquals("Werk Flow 0", metadata.getString("/workflowName"));

        // Second item on the first page of results
        currBoxItem = (BoxFile.Info) results.iterator().next();
        assertEquals("file", currBoxItem.getType());
        assertEquals("123451", currBoxItem.getID());
        assertEquals("2.jpg", currBoxItem.getName());
        metadata = currBoxItem.getMetadata("relayWorkflowInformation", "enterprise_67890");
        Assert.assertEquals("relayWorkflowInformation", metadata.getTemplateName());
        metadata = currBoxItem.getMetadata("randomTemplate", "enterprise_67890");
        Assert.assertEquals("randomTemplate", metadata.getTemplateName());
        metadata = currBoxItem.getMetadata("someTemplate", "enterprise_123456");
        Assert.assertEquals("someTemplate", metadata.getTemplateName());

        // First item on the second page of results (this next call makes the second request to get the second page)
        currBoxItem = (BoxFile.Info) results.iterator().next();
        assertEquals("file", currBoxItem.getType());
        assertEquals("123452", currBoxItem.getID());
        assertEquals("3.jpg", currBoxItem.getName());
        metadata = currBoxItem.getMetadata("relayWorkflowInformation", "enterprise_67890");
        Assert.assertEquals("relayWorkflowInformation", metadata.getTemplateName());
    }

    @Test
    @Category(UnitTest.class)
    public void testExecuteMetadataQueryWithFields() throws IOException {
        final String metadataQueryURL = "/metadata_queries/execute_read";

        final String from = "enterprise_67890.catalogImages";
        final String query = "photographer = :arg";
        final JsonObject queryParameters = new JsonObject().add("arg", "Bob Dylan");
        final String ancestorFolderId = "0";
        final String indexName = null;
        final String field1 = "sha1";
        final String field2 = "name";
        final String field3 = "id";
        final String field4 = "metadata.enterprise_240748.catalogImages.photographer";
        final JsonArray orderBy = null;
        final int limit = 2;
        final String marker = null;

        JsonArray fields = new JsonArray();
        fields.add(field1);
        fields.add(field2);
        fields.add(field3);
        fields.add(field4);
        JsonObject body = new JsonObject();
        body.add("from", from);
        body.add("query", query);
        body.add("query_params", queryParameters);
        body.add("ancestor_folder_id", ancestorFolderId);
        body.add("limit", limit);
        body.add("fields", fields);

        // First request will return a page of results with two items
        String result = TestConfig.getFixture("BoxMetadataTemplate/MetadataQueryResponseForFields200");
        WIRE_MOCK_CLASS_RULE.stubFor(post(urlPathEqualTo(metadataQueryURL))
            .withRequestBody(equalToJson(body.toString()))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(result)));

        // Make the first request and get the result
        BoxResourceIterable<BoxItem.Info> results = MetadataTemplate.executeMetadataQuery(this.api, from, query,
            queryParameters, ancestorFolderId, indexName, orderBy, limit, marker, field1, field2, field3, field4);

        // First item on the first page of results
        BoxFile.Info fileBoxItem = (BoxFile.Info) results.iterator().next();
        assertEquals("file", fileBoxItem.getType());
        assertEquals("1244738582", fileBoxItem.getID());
        assertEquals("Very Important.docx", fileBoxItem.getName());
        Metadata fileMetadata = fileBoxItem.getMetadata("catalogImages", "enterprise_67890");
        Assert.assertEquals("catalogImages", fileMetadata.getTemplateName());
        Assert.assertEquals("enterprise_67890", fileMetadata.getScope());
        Assert.assertEquals("Bob Dylan", fileMetadata.getString("/photographer"));

        // Second item on the first page of results
        BoxFolder.Info folderBoxItem = (BoxFolder.Info) results.iterator().next();
        assertEquals("folder", folderBoxItem.getType());
        assertEquals("124242482", folderBoxItem.getID());
        assertEquals("Also Important.docx", folderBoxItem.getName());
        Metadata folderMetadata = folderBoxItem.getMetadata("catalogImages", "enterprise_67890");
        Assert.assertEquals("catalogImages", folderMetadata.getTemplateName());
        Assert.assertEquals("enterprise_67890", folderMetadata.getScope());
        Assert.assertEquals("Bob Dylan", folderMetadata.getString("/photographer"));
    }
}
