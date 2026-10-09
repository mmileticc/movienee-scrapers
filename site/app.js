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

// Sample output: site/sample.json = { "snapshotDate": "yyyy-MM-dd", "movies": [ScrapedMovie...] }.
(async function loadSample() {
  const table = document.getElementById('sample-table');
  const tbody = table.querySelector('tbody');
  const filters = document.getElementById('sample-filters');
  const caption = document.getElementById('sample-caption');
  const empty = document.getElementById('sample-empty');

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
  if (data.snapshotDate) {
    caption.textContent = `A few rows from a real run on ${data.snapshotDate} - a frozen snapshot, not live data.`;
  }

  const text = (value) => document.createTextNode(value ?? '-');
  const render = (chain) => {
    tbody.replaceChildren();
    movies
      .filter((movie) => chain === 'All' || movie.cinemaName === chain)
      .forEach((movie) => {
        const row = document.createElement('tr');

        const title = document.createElement('td');
        const link = document.createElement('a');
        link.href = movie.bookingUrl;
        link.rel = 'noopener';
        link.target = '_blank';
        link.textContent = movie.title;
        title.append(link);

        const status = document.createElement('td');
        const badge = document.createElement('span');
        badge.className = `status ${movie.isUpcoming ? 'soon' : 'now'}`;
        badge.textContent = movie.isUpcoming ? 'Coming soon' : 'Now showing';
        status.append(badge);

        const cells = [movie.originalTitle, movie.cinemaName, movie.cinemaLocation].map((value) => {
          const cell = document.createElement('td');
          cell.append(text(value));
          return cell;
        });
        row.append(title, ...cells, status);
        tbody.append(row);
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
