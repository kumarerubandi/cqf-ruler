package org.opencds.cqf.common.evaluation;

import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.CqlTranslatorException;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.cql2elm.CqlTranslatorException.ErrorSeverity;
import org.cqframework.cql.cql2elm.LibraryBuilder.SignatureLevel;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.execution.VersionedIdentifier;

import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.opencds.cqf.common.helpers.TranslatorHelper.*;

public class LibraryLoader implements org.opencds.cqf.cql.execution.LibraryLoader {

    private LibraryManager libraryManager;
    private ModelManager modelManager;
    private Map<String, Library> libraries = new HashMap<>();

    // private static final Logger logger =
    // LoggerFactory.getLogger(LibraryLoader.class);

    public Collection<Library> getLibraries() {
        return this.libraries.values();
    }

    public LibraryManager getLibraryManager() {
        return this.libraryManager;
    }

    public ModelManager getModelManager() {
        return this.modelManager;
    }

    public LibraryLoader(LibraryManager libraryManager, ModelManager modelManager) {
        this.libraryManager = libraryManager;
        this.modelManager = modelManager;
    }

    private Library resolveLibrary(VersionedIdentifier libraryIdentifier) {
        if (libraryIdentifier == null) {
            throw new IllegalArgumentException("Library identifier is null.");
        }

        if (libraryIdentifier.getId() == null) {
            throw new IllegalArgumentException("Library identifier id is null.");
        }

        String mangledId = this.mangleIdentifer(libraryIdentifier);

        Library library = libraries.get(mangledId);
        if (library == null) {
            library = loadLibrary(libraryIdentifier);
            libraries.put(mangledId, library);
        }

        return library;
    }

    private String mangleIdentifer(VersionedIdentifier libraryIdentifier) {
        String id = libraryIdentifier.getId();
        String version = libraryIdentifier.getVersion();

        return version == null ? id : id + "-" + version;
    }

    private synchronized Library loadLibrary(VersionedIdentifier libraryIdentifier) {
        org.hl7.elm.r1.VersionedIdentifier identifier = new org.hl7.elm.r1.VersionedIdentifier()
                .withId(libraryIdentifier.getId()).withSystem(libraryIdentifier.getSystem())
                .withVersion(libraryIdentifier.getVersion());

        ArrayList<CqlTranslatorException> errors = new ArrayList<>();
        
        org.hl7.elm.r1.Library translatedLibrary = libraryManager.resolveLibrary(identifier, ErrorSeverity.Error,
                SignatureLevel.All,
                new CqlTranslator.Options[] { CqlTranslator.Options.EnableAnnotations,
                        CqlTranslator.Options.EnableLocators, CqlTranslator.Options.DisableListDemotion,
                        CqlTranslator.Options.DisableListPromotion, CqlTranslator.Options.DisableMethodInvocation },
                errors).getLibrary();

        if (CqlTranslatorException.HasErrors(errors)) {
            throw new IllegalArgumentException(errorsToString(errors));
        }
        try {
            CqlTranslator translator = getTranslator("", libraryManager, modelManager);

            if (translator.getErrors().size() > 0) {
                throw new IllegalArgumentException(errorsToString(translator.getErrors()));
            }
            if (libraryIdentifier == null) {
            	System.out.println("Library identifier is null");
            }
            System.out.println("The library Identidier is " + libraryIdentifier.getId());
            if(translatedLibrary == null) {
            	System.out.println("Transalated library is null");
            }
            return readLibrary(new ByteArrayInputStream(
                    translator.convertToXml(translatedLibrary).getBytes(StandardCharsets.UTF_8)));
        } catch (JAXBException e) {
            throw new IllegalArgumentException(String.format("Errors occurred translating library %s%s.",
                    identifier.getId(), identifier.getVersion() != null ? ("-" + identifier.getVersion()) : ""));
        }
    }

    @Override
    public Library load(VersionedIdentifier versionedIdentifier) {
        return resolveLibrary(versionedIdentifier);
    }
}
