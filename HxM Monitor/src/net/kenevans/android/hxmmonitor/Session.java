package net.kenevans.android.hxmmonitor;

public class Session {
	private String name;
	private long startDate= Long.MIN_VALUE;
	private long endDate= Long.MIN_VALUE;
	private boolean checked = false;

	public Session(String name, long startDate, long endDate) {
		this.name = name;
		this.startDate = startDate;
		this.endDate = endDate;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getStartDate() {
		return startDate;
	}

	public void setStartDate(long startDate) {
		this.startDate = startDate;
	}

	public long getEndDate() {
		return endDate;
	}

	public void setEndDate(long endDate) {
		this.endDate = endDate;
	}

	public long getDuration() {
		return endDate - startDate;
	}

	public boolean isChecked() {
		return checked;
	}

	public void setChecked(boolean checked) {
		this.checked = checked;
	}

}
