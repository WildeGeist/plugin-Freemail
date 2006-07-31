package freemail;

import java.io.File;
import java.lang.InterruptedException;

import freemail.utils.PropsFile;

public class SingleAccountWatcher implements Runnable {
	public static final String CONTACTS_DIR = "contacts";
	public static final String INBOUND_DIR = "inbound";
	public static final String OUTBOUND_DIR = "outbound";
	private static final int MIN_POLL_DURATION = 60000; // in milliseconds
	private static final int MAILSITE_UPLOAD_INTERVAL = 60 * 60 * 1000;
	private final MessageBank mb;
	private final NIMFetcher nf;
	private final RTSFetcher rtsf;
	private long mailsite_last_upload;
	private final PropsFile accprops;
	private final File obctdir;
	private final File ibctdir;
	private final File accdir;

	SingleAccountWatcher(File accdir) {
		this.accdir = accdir;
		this.accprops = AccountManager.getAccountFile(accdir);
		File contacts_dir = new File(accdir, CONTACTS_DIR);
		
		if (!contacts_dir.exists()) {
			contacts_dir.mkdir();
		}
		
		this.ibctdir = new File(contacts_dir, INBOUND_DIR);
		this.obctdir = new File(contacts_dir, OUTBOUND_DIR);
		this.mailsite_last_upload = 0;
		
		if (!this.ibctdir.exists()) {
			this.ibctdir.mkdir();
		}
		
		this.mb = new MessageBank(accdir.getName());
		
		File nimdir = new File(contacts_dir, AccountManager.NIMDIR);
		if (nimdir.exists()) {
			this.nf = new NIMFetcher(this.mb, nimdir);
		} else {
			this.nf = null;
		}
		
		
		this.rtsf = new RTSFetcher("KSK@"+this.accprops.get("rtskey")+"-", this.ibctdir, accdir);
		
		//this.mf = new MailFetcher(this.mb, inbound_dir, Freemail.getFCPConnection());
		
		// temporary info message until there's a nicer UI :)
		System.out.println("Freemail address: "+AccountManager.getFreemailAddress(accdir));
	}
	
	public void run() {
		while (true) {
			long start = System.currentTimeMillis();
			
			// is it time we inserted the mailsite?
			if (System.currentTimeMillis() > this.mailsite_last_upload + MAILSITE_UPLOAD_INTERVAL) {
				MailSite ms = new MailSite(this.accprops);
				if (ms.Publish() > 0) {
					this.mailsite_last_upload = System.currentTimeMillis();
				}
			}
			
			// send any messages queued in contact outboxes
			File[] obcontacts = this.obctdir.listFiles();
			if (obcontacts != null) {
				int i;
				for (i = 0; i < obcontacts.length; i++) {
					OutboundContact obct = new OutboundContact(this.accdir, obcontacts[i]);
					
					obct.doComm();
				}
			}
			
			if (this.nf != null) {
				nf.fetch();
			}
			
			this.rtsf.poll();
			
			// poll for incoming message from all inbound contacts
			File[] ibcontacts = this.ibctdir.listFiles();
			if (ibcontacts != null) {
				int i;
				for (i = 0; i < ibcontacts.length; i++) {
					if (ibcontacts[i].getName().equals(RTSFetcher.LOGFILE)) continue;
					
					InboundContact ibct = new InboundContact(this.ibctdir, ibcontacts[i].getName());
					
					ibct.fetch(this.mb);
				}
			}
			
			long runtime = System.currentTimeMillis() - start;
			
			if (MIN_POLL_DURATION - runtime > 0) {
				try {
					Thread.sleep(MIN_POLL_DURATION - runtime);
				} catch (InterruptedException ie) {
				}
			}
		}
	}
}
