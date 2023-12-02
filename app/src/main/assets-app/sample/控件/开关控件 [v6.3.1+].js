'ui';

ui.layout(<vertical gravity="center">
    <horizontal width="-1" gravity="center" margin="16">
        <text text="滑块单色/轨道单色" size="16" marginEnd="32"/>
        <switch checked="false" thumbTint="orange-800" trackTint="orange-200" marginEnd="16"></switch>
        <switch checked="true" thumbTint="orange-800" trackTint="orange-200"></switch>
    </horizontal>
    <horizontal width="-1" gravity="center" margin="16">
        <text text="滑块单色/轨道双色" size="16" marginEnd="32"/>
        <switch checked="false" thumbTint="orange-800" trackTint="light-gray/orange-200" marginEnd="16"></switch>
        <switch checked="true" thumbTint="orange-800" trackTint="light-gray/orange-200"></switch>
    </horizontal>
    <horizontal width="-1" gravity="center" margin="16">
        <text text="滑块双色/轨道单色" size="16" marginEnd="32"/>
        <switch checked="false" thumbTint="gray/orange-800" trackTint="light-gray" marginEnd="16"></switch>
        <switch checked="true" thumbTint="gray/orange-800" trackTint="light-gray"></switch>
    </horizontal>
    <horizontal width="-1" gravity="center" margin="16">
        <text text="滑块双色/轨道双色" size="16" marginEnd="32"/>
        <switch checked="false" thumbTint="gray/orange-800" trackTint="light-gray/orange-200" marginEnd="16"></switch>
        <switch checked="true" thumbTint="gray/orange-800" trackTint="light-gray/orange-200"></switch>
    </horizontal>
</vertical>);
