package geniusweb.sampleagent;

import geniusweb.actions.*;
import geniusweb.actions.Action;
import geniusweb.bidspace.AllBidsList;
import geniusweb.issuevalue.Bid;
import geniusweb.party.Capabilities;
import geniusweb.party.DefaultParty;
import geniusweb.party.inform.*;
import geniusweb.profile.PartialOrdering;
import geniusweb.profile.utilityspace.LinearAdditiveUtilitySpace;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import tudelft.utilities.logging.Reporter;
import geniusweb.deadline.DeadlineRounds;
import geniusweb.deadline.DeadlineTime;
import geniusweb.deadline.Deadline;

import javax.swing.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.Timer;
import java.util.TimerTask;


/**
 * A simple implementation of a SHAOP party that can handle only bilateral
 * negotiations (1 other party). It will ignore all other parties except the one
 * that has the turn right before us. It estimates the utilities of bids by
 * assigning a linear increasing utility from the orderings that have been
 * created.
 * <p>
 * <b>Requirement<b> the initial {@link PartialOrdering} must contain at least
 * the bids with lowest utility and highest utility, and the proper comparison
 * info for these two bids.
 */
public class MyAgent extends DefaultParty {
    private static final BigDecimal N09 = new BigDecimal("1.0");
    private final Random random = new Random();
    protected ProfileInterface profileint;
    private Bid lastReceivedBid = null; // we ignore all others
    private PartyId me;
    private Progress progress;
    private SimpleLinearOrdering estimatedProfile = null;
    //===================================================
    private static double start = System.currentTimeMillis(); //saati saymayi baslatiyor
    private static double MaxEventTime = ???; // secound cinsinden max event time
    //===================================================
//  Yaptigimiz table a gore zamani 4 e ayiracaz ama bu ayrimlar esit degil // toplam zaman = t
//  /  t0     /    t1      /   t2     /    t3      /
//  / (t/10) /  (t/10)*4 / (t/10)*3 /  (t/10)*2    /  => toplam t
//===================================================
    private static double FirstTimezone = MaxEventTime/10;
    private static double SecoundTimezone = FirstTimezone * 5;
    private static double ThirdTimezone = FirstTimezone * 7;
    //FourthTimezone = FirstTimezone * 10;
//===================================================

    public MyAgent() {}

    public MyAgent(Reporter reporter) {
        super(reporter); // for debugging
    }

    @Override
    public void notifyChange(Inform info) {
        try {
            if (info instanceof Settings) {
                Settings settings = (Settings) info;
                this.profileint = ProfileConnectionFactory.create(settings.getProfile().getURI(), getReporter());
                this.me = settings.getID();
                this.progress = settings.getProgress();
            } else if (info instanceof ActionDone) {
                Action otheract = ((ActionDone) info).getAction();
                if (otheract instanceof Offer) {
                    lastReceivedBid = ((Offer) otheract).getBid();
                } else if (otheract instanceof Comparison) {
                    estimatedProfile = estimatedProfile.with(((Comparison) otheract).getBid(), ((Comparison) otheract).getWorse());
                    myTurn();
                }
            } else if (info instanceof YourTurn) {
                myTurn();
            } else if (info instanceof Finished) {
                getReporter().log(Level.INFO, "Final ourcome:" + info);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle info", e);
        }
    }

    @Override
    public Capabilities getCapabilities() {
        return new Capabilities(new HashSet<>(Arrays.asList("SHAOP")));
    }

    @Override
    public String getDescription() {
        return "Communicates with COB party to figure out which bids are good. Accepts bids with utility > 0.9. Offers random bids. Requires partial profile";
    }

    /**
     * Called when it's (still) our turn and we should take some action. Also
     * Updates the progress if necessary.
     */
    private void myTurn() throws IOException {
        Action action = null;
        if (estimatedProfile == null) {
            estimatedProfile = new SimpleLinearOrdering(profileint.getProfile());
        }

        if (lastReceivedBid != null) {
            // then we do the action now, no need to ask user
            if (estimatedProfile.contains(lastReceivedBid)) {
                if (isAcceptable(lastReceivedBid)) {
                    action = new Accept(me, lastReceivedBid); //bidi kabul ediyo acceptable dan gecerse
                }
            }
//            else {
//                // we did not yet assess the received bid
//                action = new ElicitComparison(me, lastReceivedBid, estimatedProfile.getBids());
//            }
            if (progress instanceof ProgressRounds) {
                progress = ((ProgressRounds) progress).advance();
            }
        }
        if (action == null)
            action = offerBid(); //Bid offerliyo
        getConnection().send(action);
    }
    public boolean isAcceptable(Bid bid) throws IOException {  // Kabuledilebilir bir bidse true gonderiyo
        double valueOfThisBid = bidValueCalculator(bid);
        return valueOfThisBid >= whatIsAcceptableInThisTime(); // return boolean
    }
    public double howMuchSecoundsPassed(){ // baslangictan beri kac saniye gectigini gonderiyo
        return (start-System.currentTimeMillis())/1000; //secound cinsinden elapsed time
    }
    public Bid generateBid() throws IOException { // !!! acceptable limitten fazla yada esit olarak random bid generate ediyor !!!
        AllBidsList bidspace = new AllBidsList( profileint.getProfile().getDomain());
        Bid bid = lastReceivedBid;
        while(!isAcceptable(bid)){
            long i = random.nextInt(bidspace.size().intValue());
            bid = bidspace.get(BigInteger.valueOf(i));
        }
        return bid;
    }
    public Offer offerBid() throws IOException{  // !!! kabul edilebilir seviyeye gore bit offerliyor !!!
        return new Offer(me, generateBid());
    }

    public double bidValueCalculator(Bid bid) throws IOException {  // bidin valuesunu aliyor 0.0 dan 1.0 a kadar
        return ((LinearAdditiveUtilitySpace) profileint.getProfile()).getUtility(bid).doubleValue();
    }

    public double whatIsAcceptableInThisTime() throws IOException{ // acceptable limiti soyluyor su anki sure icin  FirstTimezone < SecoundeTimezone < ThirtTimezone < Fourth Timezone
        //Hepsi Secound cinsinden !
        if(howMuchSecoundsPassed() <= FirstTimezone){ // ilk 1/10 luk zamanda ( 10 saniye ise 1. saniyeye gelene kadar)
            return 0.8;
        }
        else if(howMuchSecoundsPassed() <= SecoundTimezone){ // 1/2 lik zaman icersinde ( 10 saniye ise 1 saniye gectikten ve 5.saniye gelmeden once)
            return 0.6;
        }
        else if(howMuchSecoundsPassed() <= ThirdTimezone){ // 7/10 luk zamanda ( 5. saniye gectikten ama 7. saniye gelmeden once
            return 0.4;
        }
        else{
            //Fourth Timezone da %20 ye kadar iniyoruz
            return 0.2; // 1 lik zamanda ( 7.saniye gecip 10. saniye gelmeden once
        }
    }



//    private Offer randomBid() throws IOException {
//        AllBidsList bidspace = new AllBidsList( profileint.getProfile().getDomain());
//        long i = random.nextInt(bidspace.size().intValue());
//        Bid bid = bidspace.get(BigInteger.valueOf(i));
//
//        return new Offer(me, bid);
//    }

//    private boolean isGood(Bid bid) {
//        if (bid == null) {
//            return false;
//        }
//
//        return estimatedProfile.getUtility(bid).compareTo(N09) >= 0;
//    }

}