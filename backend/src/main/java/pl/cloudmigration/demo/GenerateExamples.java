package pl.cloudmigration.demo;

import java.nio.file.*;
import pl.cloudmigration.documents.*;
import pl.cloudmigration.documents.DocumentCodec.Type;
import pl.cloudmigration.docx.DocxDocuments;

public final class GenerateExamples {
    public static void main(String[] args) throws Exception {
        var root=Path.of(args[0]);Files.createDirectories(root.resolve("xml"));Files.createDirectories(root.resolve("docx"));Files.createDirectories(root.resolve("invalid"));
        var codec=new DocumentCodec();var word=new DocxDocuments(codec);
        Object[] documents={Examples.competition(),Examples.application(),Examples.evaluation(),Examples.decision()};int i=0;
        for(var type:Type.values()) {
            var data=documents[i++];Files.writeString(root.resolve("xml/"+type+".xml"),codec.encode(type,data));
            Files.write(root.resolve("docx/"+type+".docx"),word.export(type,data,new DocxDocuments.Context(Examples.competition(),Examples.application(),Examples.evaluation())));
        }
        Files.write(root.resolve("docx/application-template.docx"),word.template(null));
        Files.write(root.resolve("docx/wniosek-przyklad-czytelny.docx"),word.example(Examples.competition()));
        Files.write(root.resolve("docx/ocena-przyklad-czytelny.docx"),word.export(Type.evaluation,Examples.evaluation(),new DocxDocuments.Context(Examples.competition(),Examples.application(),Examples.evaluation())));
        Files.write(root.resolve("docx/decyzja-przyklad-czytelny.docx"),word.export(Type.decision,Examples.decision(),new DocxDocuments.Context(Examples.competition(),Examples.application(),Examples.evaluation())));
        Files.writeString(root.resolve("invalid/negative-budget.xml"),codec.encode(Type.competition,Examples.competition()).replace("250000.00","-1.00"));
        Files.writeString(root.resolve("invalid/duplicate-criteria.xml"),codec.encode(Type.competition,Examples.competition()).replace(">security<",">technical<"));
        Files.writeString(root.resolve("invalid/missing-fields.xml"),"<application xmlns=\"urn:cloudmigration:application:v1\"/>");
    }
}
