package pl.cloudmigration.demo;

import java.nio.file.*;
import pl.cloudmigration.documents.*;
import pl.cloudmigration.documents.DocumentCodec.Type;
import pl.cloudmigration.docx.DocxDocuments;

/** Generate only the supported templates and their filled examples; never overwrite supplied originals. */
public final class GenerateExamples {
    public static void main(String[] args) throws Exception {
        var root=Path.of(args[0]);Files.createDirectories(root.resolve("xml"));Files.createDirectories(root.resolve("generated-docx"));Files.createDirectories(root.resolve("invalid"));
        var codec=new DocumentCodec();var word=new DocxDocuments(codec);
        var context=new DocxDocuments.Context(Examples.competition(),Examples.application(),Examples.evaluation());
        Object[] documents={Examples.competition(),Examples.application(),Examples.evaluation(),Examples.decision()};int i=0;
        for(var type:Type.values()){
            var data=documents[i++];Files.writeString(root.resolve("xml/"+type+".xml"),codec.encode(type,data));
            if(type!=Type.competition)Files.write(root.resolve("generated-docx/"+type+".docx"),word.export(type,data,context));
        }
        Files.write(root.resolve("generated-docx/application-template.docx"),word.template(null));
        Files.writeString(root.resolve("invalid/negative-budget.xml"),codec.encode(Type.competition,Examples.competition()).replace("250000.00","-1.00"));
        Files.writeString(root.resolve("invalid/duplicate-criteria.xml"),codec.encode(Type.competition,Examples.competition()).replace(">security<",">technical<"));
        Files.writeString(root.resolve("invalid/missing-fields.xml"),"<application xmlns=\"urn:cloudmigration:application:v2\"/>");
    }
}
