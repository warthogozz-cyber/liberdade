package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.PartialRequest;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;

/**
 * A partial change to the reserve. A missing {@code targetMonths} stays as it is. {@code manualMonthlyCost} tells a
 * missing field (stays) from an explicit null (clears the figure), which a record cannot: the setter notes that the
 * field came. The range and the empty body are checked by the service, which answers 400 like the other planning
 * routes; the annotations only describe the contract.
 */
@PartialRequest
final class UpdateReserveRequest {

    @JsonProperty("targetMonths")
    @Schema(nullable = true)
    @Min(1)
    @Max(24)
    private @Nullable Integer targetMonths;

    @JsonProperty("manualMonthlyCost")
    @Schema(nullable = true)
    private @Nullable Money manualMonthlyCost;

    private boolean manualMonthlyCostGiven;

    @JsonSetter("manualMonthlyCost")
    void setManualMonthlyCost(@Nullable Money manualMonthlyCost) {
        this.manualMonthlyCost = manualMonthlyCost;
        this.manualMonthlyCostGiven = true;
    }

    @JsonSetter("targetMonths")
    void setTargetMonths(@Nullable Integer targetMonths) {
        this.targetMonths = targetMonths;
    }

    @JsonIgnore
    @Nullable
    Integer targetMonths() {
        return targetMonths;
    }

    @JsonIgnore
    @Nullable
    Money manualMonthlyCost() {
        return manualMonthlyCost;
    }

    /** True when the body had {@code manualMonthlyCost}, even as null. */
    @JsonIgnore
    boolean manualMonthlyCostGiven() {
        return manualMonthlyCostGiven;
    }

    /** True when the body changes nothing: neither field came. */
    @JsonIgnore
    boolean isEmpty() {
        return targetMonths == null && !manualMonthlyCostGiven;
    }
}
