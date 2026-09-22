package com.chintu.anything.plan;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.chintu.anything.parser.ParseResult;
import com.chintu.anything.parser.ParsedDay;
import com.chintu.anything.parser.ParsedItem;
import com.chintu.anything.parser.ParsedPhase;
import com.chintu.anything.parser.ParsedPlan;
import com.chintu.anything.parser.PlanParser;
import com.chintu.anything.plan.PlanResponses.PlanDetail;
import com.chintu.anything.plan.PlanResponses.PlanSummary;

/**
 * Saves, lists and deletes plans.
 *
 * <p>The plan text is parsed again on save, even though the web app already
 * previewed it. The server never trusts that the client sent a valid plan.
 */
@Service
public class PlanService {

    private final PlanParser parser;
    private final PlanRepository plans;

    public PlanService(PlanParser parser, PlanRepository plans) {
        this.parser = parser;
        this.plans = plans;
    }

    @Transactional
    public PlanDetail create(CreatePlanRequest request) {
        ParseResult result = parser.parse(request.text());
        if (!result.isOk()) {
            throw new InvalidPlanException(result.errors());
        }
        Plan plan = toEntity(result.plan(), request);
        return PlanDetail.from(plans.save(plan));
    }

    @Transactional(readOnly = true)
    public List<PlanSummary> list() {
        return plans.findAllByOrderByCreatedAtDesc().stream().map(PlanSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public PlanDetail get(long id) {
        return PlanDetail.from(find(id));
    }

    @Transactional
    public void delete(long id) {
        plans.delete(find(id));
    }

    private Plan find(long id) {
        return plans.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan " + id + " not found."));
    }

    private static Plan toEntity(ParsedPlan parsed, CreatePlanRequest request) {
        var header = parsed.header();
        Plan plan = new Plan(header.title(), header.category(), header.weeks(),
                request.startDate(), request.text());

        for (ParsedPhase p : parsed.phases()) {
            PlanPhase phase = new PlanPhase(p.fromWeek(), p.toWeek(), p.name());
            List<ParsedDay> days = p.days();
            for (int d = 0; d < days.size(); d++) {
                ParsedDay pd = days.get(d);
                PlanDay day = new PlanDay(pd.weekday(), pd.title(), d);
                List<ParsedItem> items = pd.items();
                for (int i = 0; i < items.size(); i++) {
                    ParsedItem pi = items.get(i);
                    day.addItem(new PlanItem(i, pi.name(), pi.sets(), pi.reps(), pi.restSeconds(), pi.note()));
                }
                phase.addDay(day);
            }
            plan.addPhase(phase);
        }
        return plan;
    }
}
