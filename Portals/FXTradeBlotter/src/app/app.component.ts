import { Component } from '@angular/core';

@Component({
  selector: 'app-root',
  standalone: true,
  template: `<h1>{{title}}</h1>`,
})
export class AppComponent {
  title = 'FX Trade Blotter Portal';
}
