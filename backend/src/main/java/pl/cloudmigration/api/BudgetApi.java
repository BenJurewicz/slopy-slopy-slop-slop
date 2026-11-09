package pl.cloudmigration.api;

import java.math.*;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import pl.cloudmigration.documents.Models.BudgetLine;
import pl.cloudmigration.workflow.Rules;

@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400",description="Nieprawidłowe żądanie",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404",description="Brak zasobu",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="409",description="Konflikt rewizji lub procesu",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="413",description="Za duży plik",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="422",description="Błąd walidacji",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="503",description="Baza niedostępna",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=pl.cloudmigration.api.Errors.Error.class)))
})
@RestController
@RequestMapping("/api/budget")
public class BudgetApi {
    public record Calculated(List<BudgetLine> lines,String total,String ownContribution) {}
    @Operation(operationId="calculateBudget")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200",description="Operacja wykonana",useReturnTypeSchema=true)
    @PostMapping("/calculate") public Calculated calculate(@RequestBody List<BudgetLine> lines,@RequestParam(required=false) String requestedAmount){
        if(lines==null||lines.isEmpty())throw AppException.invalid("Dodaj pozycje budżetu.");
        var calculated=lines.stream().map(line->{
            try {
                var quantity=Rules.quantity(line.quantity());
                var total=quantity.multiply(Rules.money(line.unitCost())).setScale(2,RoundingMode.HALF_UP);
                return new BudgetLine(line.description(),line.category(),line.quantity(),line.unit(),line.unitCost(),total.toPlainString());
            }catch(AppException e){throw e;}catch(Exception e){throw AppException.invalid("Nieprawidłowa ilość lub koszt jednostkowy.");}
        }).toList();
        var total=calculated.stream().map(l->new BigDecimal(l.total())).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2);
        var own=requestedAmount==null?null:total.subtract(Rules.money(requestedAmount));
        Rules.require(own==null||own.signum()>=0,"Dotacja przekracza koszty projektu.");
        return new Calculated(calculated,total.toPlainString(),own==null?null:own.toPlainString());
    }
}
