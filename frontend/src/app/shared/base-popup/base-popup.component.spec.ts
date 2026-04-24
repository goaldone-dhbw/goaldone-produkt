import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';

import { BasePopupComponent } from './base-popup.component';

@Component({
  standalone: true,
  imports: [BasePopupComponent],
  template: `
    <app-base-popup
      [isOpen]="isStandardOpen"
      [title]="standardTitle"
      [content]="standardContent"
      (confirm)="onStandardConfirm()"
      (cancel)="onStandardCancel()"
      (closed)="onStandardClosed()"
    ></app-base-popup>

    <app-base-popup [isOpen]="isNoTitleOpen" content="Ohne Titel"></app-base-popup>

    <app-base-popup [isOpen]="isNoListenerOpen" title="Ohne Listener" content="Kein Confirm-Listener"></app-base-popup>

    <app-base-popup [isOpen]="isTemplateOpen" title="Template Test" [content]="templateDescription">
      @if (renderTemplateContent) {
        <div popup-content>Benutzerdefinierter Template-Inhalt</div>
      }
    </app-base-popup>
  `,
})
class HostComponent {
  isStandardOpen = false;
  isNoTitleOpen = false;
  isNoListenerOpen = false;
  isTemplateOpen = false;

  standardTitle = 'Bestaetigung';
  standardContent = 'Moechten Sie fortfahren?';
  templateDescription = '';
  renderTemplateContent = true;

  confirmCalls = 0;
  cancelCalls = 0;
  closedCalls = 0;

  onStandardConfirm(): void {
    this.confirmCalls += 1;
    this.isStandardOpen = false;
  }

  onStandardCancel(): void {
    this.cancelCalls += 1;
    this.isStandardOpen = false;
  }

  onStandardClosed(): void {
    this.closedCalls += 1;
    this.isStandardOpen = false;
  }
}

describe('BasePopupComponent system tests', () => {
  const POPUP_INDEX = {
    standard: 0,
    noTitle: 1,
    noListener: 2,
    template: 3,
  } as const;

  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
  });

  function getButtons(): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('button'));
  }

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function getRenderedText(): string {
    return fixture.nativeElement.textContent ?? '';
  }

  function getPopupInstance(index: number): BasePopupComponent {
    const popupInstances = fixture.debugElement
      .queryAll(By.directive(BasePopupComponent))
      .map((node) => node.componentInstance as BasePopupComponent);

    return popupInstances[index];
  }

  function clickButtonByLabel(label: string): void {
    const button = getButtons().find((candidate) => candidate.textContent?.includes(label));
    expect(button).toBeTruthy();
    button!.click();
    fixture.detectChanges();
  }

  it('shows default popup with title, content and standard buttons', async () => {
    host.isStandardOpen = true;
    await render();

    const text = getRenderedText();
    expect(text).toContain('Bestaetigung');
    expect(text).toContain('Moechten Sie fortfahren?');
    expect(text).toContain('Ok');
    expect(text).toContain('Abbrechen');
  });

  it('emits confirm event and closes popup after clicking Ok', async () => {
    host.isStandardOpen = true;
    await render();

    clickButtonByLabel('Ok');
    await fixture.whenStable();

    expect(host.confirmCalls).toBe(1);
    expect(host.isStandardOpen).toBe(false);
  });

  it('renders dynamic projected template content and keeps footer layout', async () => {
    host.isTemplateOpen = true;
    host.templateDescription = 'Zusatzbeschreibung';
    await render();

    const text = getRenderedText();
    expect(text).toContain('Benutzerdefinierter Template-Inhalt');
    expect(text).toContain('Ok');
    expect(text).toContain('Abbrechen');
  });

  it('stays stable when title is missing and falls back to default title', async () => {
    host.isNoTitleOpen = true;
    await render();

    expect(() => fixture.detectChanges()).not.toThrow();
    expect(getRenderedText()).toContain('Hinweis');
  });

  it('does not crash and still closes when confirm listener is missing', async () => {
    host.isNoListenerOpen = true;
    await render();

    const noListenerPopup = getPopupInstance(POPUP_INDEX.noListener);

    expect(noListenerPopup).toBeTruthy();
    expect(() => noListenerPopup.onConfirm()).not.toThrow();

    expect(noListenerPopup.dialogVisible).toBe(false);
  });

  it('stays stable for empty projected content', async () => {
    host.isTemplateOpen = true;
    host.renderTemplateContent = false;
    host.templateDescription = '';
    await render();

    expect(() => fixture.detectChanges()).not.toThrow();
    expect(getRenderedText()).toContain('Template Test');
  });
});
