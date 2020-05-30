package geniusweb.exampleparties.randomparty;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.logging.Level;

import geniusweb.actions.Accept;
import geniusweb.actions.Action;
import geniusweb.actions.Offer;
import geniusweb.actions.PartyId;
import geniusweb.bidspace.AllPartialBidsList;
import geniusweb.issuevalue.Bid;
import geniusweb.party.Capabilities;
import geniusweb.party.DefaultParty;
import geniusweb.party.inform.ActionDone;
import geniusweb.party.inform.Finished;
import geniusweb.party.inform.Inform;
import geniusweb.party.inform.Settings;
import geniusweb.party.inform.YourTurn;
import geniusweb.profile.PartialOrdering;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.UtilitySpace;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import tudelft.utilities.logging.Reporter;

/**
 * A simple party that places random bids and accepts when it receives an offer
 * with sufficient utility.
 */
public class RandomParty extends DefaultParty {

	private Bid lastReceivedBid = null;
	private PartyId me;
	private final Random random = new Random();
	protected ProfileInterface profileint;
	private Progress progress;

	public RandomParty() {
	}

	public RandomParty(Reporter reporter) {
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
				}
			} else if (info instanceof YourTurn) {
				myTurn();
				if (progress instanceof ProgressRounds) {
					progress = ((ProgressRounds) progress).advance();
				}
			} else if (info instanceof Finished) {
				getReporter().log(Level.INFO, "Final ourcome:" + info);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to handle info", e);
		}
	}

	@Override
	public Capabilities getCapabilities() {
		return new Capabilities(new HashSet<>(Arrays.asList("SAOP")));
	}

	@Override
	public String getDescription() {
		return "places random bids until it can accept an offer with utility >0.6";
	}

	private void myTurn() throws IOException {
		Action action;
		if (isGood(lastReceivedBid)) {
			action = new Accept(me, lastReceivedBid);
		} else {
			// for demo. Obviously full bids have higher util in general
			AllPartialBidsList bidspace = new AllPartialBidsList(profileint.getProfile().getDomain());
			Bid bid = null;
			for (int attempt = 0; attempt < 20 && !isGood(bid); attempt++) {
				long i = random.nextInt(bidspace.size().intValue());
				bid = bidspace.get(BigInteger.valueOf(i));
			}
			action = new Offer(me, bid);
		}
		getConnection().send(action);

	}

	private boolean isGood(Bid bid) throws IOException {
		if (bid == null)
			return false;
		Profile profile = profileint.getProfile();
		if (profile instanceof UtilitySpace) {
			return ((UtilitySpace) profile).getUtility(bid).doubleValue() > 0.6;
		}
		if (profile instanceof PartialOrdering) {
			return ((PartialOrdering) profile).isPreferredOrEqual(bid,
					profile.getReservationBid());
		}
		throw new IllegalArgumentException(
				"Can not handle profile type " + profile.getClass());
	}

}
