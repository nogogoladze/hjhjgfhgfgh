package ge.dcs.rms.controller;

import ge.dcs.rms.common.Stuff;
import ge.dcs.rms.common.dto.OperatorDTO;
import ge.dcs.rms.common.dto.Response;
import ge.dcs.rms.component.SessionComponent;
import ge.dcs.rms.config.Utils;
import ge.dcs.rms.service.StuffService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.Map;

@Controller
@RequestMapping(value = "/Secure", produces = {MediaType.APPLICATION_JSON_VALUE}, consumes = {MediaType.ALL_VALUE})
public class SessionController {
    private static final Logger logger = LogManager.getLogger(SessionController.class);

    @Autowired
    private StuffService stuffService;

    @Autowired
    private SessionComponent sessionComponent;

    /**
     * სისტემიდან გასვლა, operator ქვია ყველა იმ თანამშრომელს, რომელიც სარგებლობს პროგრამით
     * TODO ლოგებია ჩასამატებელი
     * TODO ყველა დაბრუნებული პასუხი ტექსტად, მომავალში გადასაკეთებელი იქნება, როგორც კონსტანტა და გამოყენებული იქნება ენებისთვის
     *
     * @param httpSession
     * @return
     */
    @GetMapping("/SignOut")
    @ResponseBody
    public Response signOut(HttpSession httpSession) {
        logger.info("Started operator sign out action");

        if (checkSession().isError()) {
            logger.error("No active operator found in session");
            return Response.error("No active operator found in session");
        }

        Stuff stuffMember = stuffService.getStuffMemberById(((OperatorDTO) checkSession().getData()).getOperatorId());

        /**
         * TODO სანახავია ეს მეთოდი უნდა აბრუნებდეს თუ არა რამეს
         */
        sessionComponent.clearSession(stuffMember, httpSession);

        logger.info("Operator '" + stuffMember.getFullName() + "' successfully signed out from system");
        return Response.ok();
    }

    /**
     * სისტემაში ავტორიზაცია
     *
     * @param parameters
     * @param httpSession
     * @return
     */
    @PostMapping("/Login")
    @ResponseBody
    public Response login(@RequestParam Map<String, String> parameters, HttpSession httpSession) {
        logger.info("Started operator login action. " + Utils.paramsToString(parameters));

        if (parameters.get("pin").isEmpty()) {
            return Response.error("Requested pin is empty for operator authorization");
        }

        Stuff stuffMember = stuffService.getStuffMemberByPin(Utils.strToInteger(parameters.get("pin")));
        if (stuffMember == null) {
            return Response.error("Operator with specified pin does not exist: " + Utils.strToInteger(parameters.get("pin")));
        }

        /**
         * თუ უკვე ავტორიზირებულია მომხმარებელი, დუბლირებული ავტორიზაციის თავიდან ასაცილებლად წარმატებას ვუბრუნებთ და ვაჩერებთ მეთოდს
         */
        if (!checkSession().isError()) {
            logger.warn("Operator already logged in. " + checkSession().getData());
            return checkSession();
        }

        /**
         * ახალი სესიის შექმნა
         */

        logger.info("Creating new session for operator: " + stuffMember.getFullName());
        sessionComponent.createSession(stuffMember, Integer.parseInt(parameters.get("locationId")), httpSession);

        sessionComponent.getRunningSession();
        return Response.ok();
    }

    @RequestMapping(value = "/CheckSession", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseBody
    public Response checkSession() {
        if (sessionComponent.getRunningSession() == null) {
            return Response.error("Operator not logged in");
        }
        return Response.ok(sessionComponent.getRunningSession());
    }
}
