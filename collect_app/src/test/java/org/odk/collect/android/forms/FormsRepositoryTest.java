package org.odk.collect.android.forms;

import org.junit.Test;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.utilities.Clock;

import java.io.File;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.odk.collect.android.support.FormUtils.buildForm;

public abstract class FormsRepositoryTest {

    public abstract FormsRepository buildSubject();

    public abstract FormsRepository buildSubject(Clock clock);

    public abstract String getFormFilesPath();

    @Test
    public void getLatestByFormIdAndVersion_whenFormHasNullVersion_returnsForm() {
        FormsRepository formsRepository = buildSubject();
        formsRepository.save(buildForm("1", null, getFormFilesPath())
                .build());

        Form form = formsRepository.getLatestByFormIdAndVersion("1", null);
        assertThat(form, notNullValue());
        assertThat(form.getId(), is(1L));
    }

    @Test
    public void getLatestByFormIdAndVersion_whenMultipleExist_returnsLatest() {
        Clock mockClock = mock(Clock.class);
        when(mockClock.getCurrentTime()).thenReturn(2L, 3L, 1L);

        FormsRepository formsRepository = buildSubject(mockClock);
        formsRepository.save(buildForm("1", "1", getFormFilesPath())
                .build());
        formsRepository.save(buildForm("1", "1", getFormFilesPath())
                .build());
        formsRepository.save(buildForm("1", "1", getFormFilesPath())
                .build());

        Form form = formsRepository.getLatestByFormIdAndVersion("1", "1");
        assertThat(form, notNullValue());
        assertThat(form.getId(), is(2L));
    }

    @Test
    public void getAllByFormIdAndVersion_whenFormHasNullVersion_returnsAllMatchingForms() {
        FormsRepository formsRepository = buildSubject();
        formsRepository.save(buildForm("1", null, getFormFilesPath())
                .build());

        formsRepository.save(buildForm("1", null, getFormFilesPath())
                .build());

        formsRepository.save(buildForm("1", "7", getFormFilesPath())
                .build());

        List<Form> forms = formsRepository.getAllByFormIdAndVersion("1", null);
        assertThat(forms.size(), is(2));
        assertThat(forms.get(0).getJrVersion(), is(nullValue()));
        assertThat(forms.get(1).getJrVersion(), is(nullValue()));
    }

    @Test
    public void getAllNotDeletedByFormId_doesNotReturnDeletedForms() {
        FormsRepository formsRepository = buildSubject();
        formsRepository.save(buildForm("1", "deleted", getFormFilesPath())
                .deleted(true)
                .build()
        );

        formsRepository.save(buildForm("1", "not-deleted", getFormFilesPath())
                .deleted(false)
                .build()
        );

        List<Form> forms = formsRepository.getAllNotDeletedByFormId("1");
        assertThat(forms.size(), is(1));
        assertThat(forms.get(0).getJrVersion(), equalTo("not-deleted"));
    }

    @Test
    public void getAllNotDeletedByFormIdAndVersion_onlyReturnsNotDeletedFormsThatMatchVersion() {
        FormsRepository formsRepository = buildSubject();
        formsRepository.save(buildForm("id", "1", getFormFilesPath())
                .deleted(true)
                .build()
        );
        formsRepository.save(buildForm("id", "1", getFormFilesPath())
                .deleted(false)
                .build()
        );

        formsRepository.save(buildForm("id", "2", getFormFilesPath())
                .deleted(true)
                .build()
        );
        formsRepository.save(buildForm("id", "2", getFormFilesPath())
                .deleted(false)
                .build()
        );

        List<Form> forms = formsRepository.getAllNotDeletedByFormIdAndVersion("id", "2");
        assertThat(forms.size(), is(1));
        assertThat(forms.get(0).getJrVersion(), equalTo("2"));
    }

    @Test
    public void softDelete_marksDeletedAsTrue() {
        FormsRepository formsRepository = buildSubject();
        formsRepository.save(buildForm("1", null, getFormFilesPath())
                .build());

        formsRepository.softDelete(1L);
        assertThat(formsRepository.get(1L).isDeleted(), is(true));
    }

    @Test
    public void restore_marksDeletedAsFalse() {
        FormsRepository formsRepository = buildSubject();
        formsRepository.save(buildForm("1", null, getFormFilesPath())
                .deleted(true)
                .build());

        formsRepository.restore(1L);
        assertThat(formsRepository.get(1L).isDeleted(), is(false));
    }

    @Test
    public void save_addsId() {
        FormsRepository formsRepository = buildSubject();
        Form form = buildForm("id", "version", getFormFilesPath()).build();

        formsRepository.save(form);
        assertThat(formsRepository.getAll().get(0).getId(), notNullValue());
    }

    @Test
    public void save_addsHashBasedOnFormFile() {
        FormsRepository formsRepository = buildSubject();
        Form form = buildForm("id", "version", getFormFilesPath()).build();
        assertThat(form.getMD5Hash(), equalTo(null));

        formsRepository.save(form);

        String expectedHash = FileUtils.getMd5Hash(new File(form.getFormFilePath()));
        assertThat(formsRepository.get(1L).getMD5Hash(), equalTo(expectedHash));
    }

    @Test(expected = Exception.class)
    public void save_whenNoFormFilePath_explodes() {
        FormsRepository formsRepository = buildSubject();
        Form form = buildForm("id", "version", getFormFilesPath()).build();
        form = new Form.Builder(form)
                .formFilePath(null)
                .build();

        formsRepository.save(form);
    }

    @Test
    public void save_whenFormHasId_updatesExisting() {
        FormsRepository formsRepository = buildSubject();
        Form originalForm = formsRepository.save(buildForm("id", "version", getFormFilesPath())
                .displayName("original")
                .build());

        formsRepository.save(new Form.Builder(originalForm)
                .displayName("changed")
                .build());

        assertThat(formsRepository.get(originalForm.getId()).getDisplayName(), is("changed"));
    }

    @Test
    public void delete_deletesFiles() {
        FormsRepository formsRepository = buildSubject();
        Form form = formsRepository.save(buildForm("id", "version", getFormFilesPath()).build());

        // FormRepository currently doesn't manage media file path other than deleting it
        String mediaPath = FileUtils.constructMediaPath(form.getFormFilePath());
        new File(mediaPath).mkdir();

        File formFile = new File(form.getFormFilePath());
        File mediaDir = new File(form.getFormMediaPath());
        assertThat(formFile.exists(), is(true));
        assertThat(mediaDir.exists(), is(true));

        formsRepository.delete(1L);
        assertThat(formFile.exists(), is(false));
        assertThat(mediaDir.exists(), is(false));
    }

    @Test
    public void delete_whenMediaPathIsFile_deletesFiles() throws Exception {
        FormsRepository formsRepository = buildSubject();
        Form form = formsRepository.save(buildForm("id", "version", getFormFilesPath()).build());

        // FormRepository currently doesn't manage media file path other than deleting it
        String mediaPath = FileUtils.constructMediaPath(form.getFormFilePath());
        new File(mediaPath).createNewFile();

        File formFile = new File(form.getFormFilePath());
        File mediaDir = new File(form.getFormMediaPath());
        assertThat(formFile.exists(), is(true));
        assertThat(mediaDir.exists(), is(true));

        formsRepository.delete(1L);
        assertThat(formFile.exists(), is(false));
        assertThat(mediaDir.exists(), is(false));
    }

    @Test
    public void deleteAll_deletesAllForms() {
        FormsRepository formsRepository = buildSubject();
        formsRepository.save(buildForm("id1", "version", getFormFilesPath()).build());
        formsRepository.save(buildForm("id2", "version", getFormFilesPath()).build());

        List<Form> forms = formsRepository.getAll();

        formsRepository.deleteAll();
        assertThat(formsRepository.getAll().size(), is(0));

        for (Form form : forms) {
            assertThat(new File(form.getFormFilePath()).exists(), is(false));
            assertThat(new File(form.getFormMediaPath()).exists(), is(false));
        }
    }

    @Test
    public void deleteByMd5Hash_deletesFormsWithMatchingHash() {
        FormsRepository formsRepository = buildSubject();
        formsRepository.save(buildForm("id1", "version", getFormFilesPath()).build());
        formsRepository.save(buildForm("id1", "version", getFormFilesPath()).build());
        formsRepository.save(buildForm("id2", "version", getFormFilesPath()).build());

        List<Form> id1Forms = formsRepository.getAllByFormIdAndVersion("id1", "version");
        assertThat(id1Forms.size(), is(2));
        assertThat(id1Forms.get(0).getMD5Hash(), is(id1Forms.get(1).getMD5Hash()));

        formsRepository.deleteByMd5Hash(id1Forms.get(0).getMD5Hash());
        assertThat(formsRepository.getAll().size(), is(1));
        assertThat(formsRepository.getAll().get(0).getJrFormId(), is("id2"));
    }

    @Test(expected = Exception.class)
    public void getOneByMd5Hash_whenHashIsNull_explodes() {
        buildSubject().getOneByMd5Hash(null);
    }

    @Test
    public void getOneByMd5Hash_returnsMatchingForm() {
        FormsRepository formsRepository = buildSubject();
        formsRepository.save(buildForm("id1", "version", getFormFilesPath()).build());
        Form form2 = formsRepository.save(buildForm("id2", "version", getFormFilesPath()).build());

        assertThat(formsRepository.getOneByMd5Hash(form2.getMD5Hash()), is(form2));
    }

    @Test
    public void getOneByPath_returnsMatchingForm() {
        FormsRepository formsRepository = buildSubject();
        formsRepository.save(buildForm("id1", "version", getFormFilesPath()).build());

        Form form2 = buildForm("id2", "version", getFormFilesPath()).build();
        formsRepository.save(form2);

        assertThat(formsRepository.getOneByPath(form2.getFormFilePath()).getJrFormId(), is("id2"));
    }

    @Test
    public void getAllFormId_returnsMatchingForms() {
        FormsRepository formsRepository = buildSubject();
        Form form1 = formsRepository.save(buildForm("id1", "version", getFormFilesPath()).build());
        Form form2 = formsRepository.save(buildForm("id1", "other_version", getFormFilesPath()).build());
        formsRepository.save(buildForm("id2", "version", getFormFilesPath()).build());

        List<Form> forms = formsRepository.getAllByFormId("id1");
        assertThat(forms.size(), is(2));
        assertThat(forms, contains(form1, form2));
    }
}
