// Copy buttons on the code blocks.
document.querySelectorAll('.copy').forEach((button) => {
  button.addEventListener('click', async () => {
    const text = button.parentElement.querySelector('code').innerText;
    try {
      await navigator.clipboard.writeText(text);
      button.textContent = 'Copied';
    } catch {
      button.textContent = 'Select & copy';
    }
    setTimeout(() => (button.textContent = 'Copy'), 1500);
  });
});

// Sample output: site/sample.json =
// { snapshotDate, totals: {chains, locations, rows, showtimes}, stats: [{cinemaName, movies, locations, showtimes}],
//   movies: [ScrapedMovie with showtimes] }
(async function loadSample() {
  const table = document.getElementById('sample-table');
  const tbody = table.querySelector('tbody');
  const filters = document.getElementById('sample-filters');
  const caption = document.getElementById('sample-caption');
  const empty = document.getElementById('sample-empty');
  const statsGrid = document.getElementById('run-stats');

  let data;
  try {
    const response = await fetch('sample.json', { cache: 'no-cache' });
    if (!response.ok) throw new Error(response.statusText);
    data = await response.json();
  } catch {
    empty.hidden = false;
    return;
  }

  const movies = Array.isArray(data) ? data : data.movies || [];
  if (movies.length === 0) {
    empty.hidden = false;
    return;
  }

  // Dates are local Belgrade times without a zone ("2026-10-15T19:00"); they are shown as written,
  // not converted to the visitor's time zone.
  const dayName = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' });
  const parts = (startsAt) => {
    const [date, time] = startsAt.split('T');
    const [y, m, d] = date.split('-').map(Number);
    return { date, time: time.slice(0, 5), label: dayName.format(new Date(Date.UTC(y, m - 1, d))) };
  };
  const number = new Intl.NumberFormat('en-GB');

  if (data.snapshotDate) {
    caption.textContent =
      `A frozen snapshot of a real run on ${data.snapshotDate}: a few movies per chain with their ` +
      `showtimes. Click a row to see its screenings - ticket links of past screenings no longer work.`;
  }

  // Totals of the whole run, per chain.
  if (statsGrid && Array.isArray(data.stats)) {
    for (const s of data.stats) {
      const card = document.createElement('div');
      card.className = 'run-card';
      const name = document.createElement('h3');
      name.textContent = s.cinemaName;
      const showtimes = document.createElement('p');
      showtimes.className = 'big';
      showtimes.textContent = number.format(s.showtimes);
      const label = document.createElement('p');
      label.className = 'small';
      label.textContent = `showtimes · ${s.movies} movies · ${s.locations} ${s.locations === 1 ? 'location' : 'locations'}`;
      card.append(name, showtimes, label);
      statsGrid.append(card);
    }
    if (data.totals) {
      document.getElementById('run-total').textContent =
        `${number.format(data.totals.showtimes)} showtimes at ${data.totals.locations} locations of ` +
        `${data.totals.chains} chains in one run (${number.format(data.totals.rows)} movie rows).`;
    }
    statsGrid.parentElement.hidden = false;
  }

  const cell = (value) => {
    const td = document.createElement('td');
    td.textContent = value ?? '-';
    return td;
  };

  /** The screenings of one movie, grouped by day; every time links to that screening's ticket page. */
  const details = (movie) => {
    const box = document.createElement('div');
    box.className = 'showtimes';
    if (movie.showtimes.length === 0) {
      const note = document.createElement('p');
      note.className = 'small';
      note.textContent = 'No screenings scheduled in the next 7 days yet.';
      box.append(note);
      return box;
    }
    const byDay = new Map();
    for (const s of movie.showtimes) {
      const p = parts(s.startsAt);
      if (!byDay.has(p.date)) byDay.set(p.date, { label: p.label, items: [] });
      byDay.get(p.date).items.push({ ...s, time: p.time });
    }
    for (const { label, items } of byDay.values()) {
      const day = document.createElement('div');
      day.className = 'day';
      const heading = document.createElement('span');
      heading.className = 'day-label';
      heading.textContent = label;
      const times = document.createElement('div');
      times.className = 'times';
      for (const s of items) {
        const slot = document.createElement(s.bookingUrl ? 'a' : 'span');
        slot.className = 'slot';
        if (s.bookingUrl) {
          slot.href = s.bookingUrl;
          slot.target = '_blank';
          slot.rel = 'noopener nofollow';
        }
        const time = document.createElement('b');
        time.textContent = s.time;
        slot.append(time);
        const extra = [s.hall, s.format, s.priceRsd ? `${s.priceRsd} RSD` : null].filter(Boolean).join(' · ');
        if (extra) {
          const meta = document.createElement('small');
          meta.textContent = extra;
          slot.append(meta);
        }
        slot.title = `${label} ${s.time}${extra ? ' - ' + extra : ''}`;
        times.append(slot);
      }
      day.append(heading, times);
      box.append(day);
    }
    return box;
  };

  const render = (chain) => {
    tbody.replaceChildren();
    movies
      .filter((movie) => chain === 'All' || movie.cinemaName === chain)
      .forEach((movie, index) => {
        const showtimes = movie.showtimes || [];
        const row = document.createElement('tr');
        row.className = 'movie-row';

        const titleCell = document.createElement('td');
        const toggle = document.createElement('button');
        toggle.type = 'button';
        toggle.className = 'toggle';
        toggle.setAttribute('aria-expanded', 'false');
        const detailsId = `showtimes-${chain}-${index}`.replace(/\W+/g, '-');
        toggle.setAttribute('aria-controls', detailsId);
        const title = document.createElement('span');
        title.className = 'title';
        title.textContent = movie.title;
        toggle.append(title);
        if (movie.originalTitle) {
          const original = document.createElement('small');
          original.textContent = movie.originalTitle;
          toggle.append(original);
        }
        titleCell.append(toggle);

        const next = showtimes[0] ? parts(showtimes[0].startsAt) : null;
        const status = document.createElement('td');
        const badge = document.createElement('span');
        badge.className = `status ${movie.isUpcoming ? 'soon' : 'now'}`;
        badge.textContent = movie.isUpcoming ? (showtimes.length ? 'Opens this week' : 'Coming soon') : 'Now showing';
        status.append(badge);

        row.append(
          titleCell,
          cell(movie.cinemaName),
          cell(movie.cinemaLocation),
          cell(next ? `${next.label} ${next.time}` : null),
          cell(showtimes.length ? String(showtimes.length) : null),
          status,
        );

        const detailsRow = document.createElement('tr');
        detailsRow.className = 'details-row';
        detailsRow.id = detailsId;
        detailsRow.hidden = true;
        const detailsCell = document.createElement('td');
        detailsCell.colSpan = 6;
        const page = document.createElement('a');
        page.className = 'movie-link';
        page.href = movie.bookingUrl;
        page.target = '_blank';
        page.rel = 'noopener nofollow';
        page.textContent = 'Movie page on the cinema website →';
        detailsCell.append(details(movie), page);
        detailsRow.append(detailsCell);

        toggle.addEventListener('click', () => {
          const open = detailsRow.hidden;
          detailsRow.hidden = !open;
          toggle.setAttribute('aria-expanded', String(open));
          row.classList.toggle('open', open);
        });

        tbody.append(row, detailsRow);
      });
  };

  const chains = ['All', ...new Set(movies.map((movie) => movie.cinemaName))];
  chains.forEach((chain) => {
    const chip = document.createElement('button');
    chip.type = 'button';
    chip.className = 'chip';
    chip.textContent = chain;
    chip.setAttribute('aria-pressed', String(chain === 'All'));
    chip.addEventListener('click', () => {
      filters.querySelectorAll('.chip').forEach((other) => other.setAttribute('aria-pressed', String(other === chip)));
      render(chain);
    });
    filters.append(chip);
  });

  filters.hidden = false;
  table.hidden = false;
  render('All');
})();
